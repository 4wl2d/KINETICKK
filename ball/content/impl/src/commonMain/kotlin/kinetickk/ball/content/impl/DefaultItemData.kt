// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.ARSENAL
import kinetickk.ball.content.api.BuildTag.BULWARK
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.FORTUNE
import kinetickk.ball.content.api.BuildTag.OVERCLOCK
import kinetickk.ball.content.api.BuildTag.PRECISION
import kinetickk.ball.content.api.BuildTag.RAM
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ConversionKind
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.FieldAspect
import kinetickk.ball.content.api.ImpulseDirection
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.LEGENDARY
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.MarkPick
import kinetickk.ball.content.api.ReactionAspect
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.SkillDefinition
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.SkillPick
import kinetickk.ball.content.api.StatDelta
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.ZoneKind
import kinetickk.ball.content.api.defaultSkillDefinitions
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

// Compact authoring vocabulary. Every value is validated by the api constructors.
private fun stat(effect: ItemEffect, amount: Float) = StatDelta(effect, amount)

private fun on(
    trigger: EffectTrigger,
    vararg actions: EffectAction,
    given: List<EffectCondition> = emptyList(),
    cooldown: Float = 0f,
    every: Int = 1,
) = EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown, every)

private fun apply(element: StatusElement, stacks: Int, radius: Float = 0f) =
    EffectAction.ApplyStatus(ElementPick.of(element), stacks, radius)

private fun applyPick(pick: ElementPick, stacks: Int, radius: Float = 0f) =
    EffectAction.ApplyStatus(pick, stacks, radius)

private class ItemDraft(
    val name: String,
    val flavor: String,
    val rarity: ItemRarity,
    val tags: List<BuildTag>,
    val elements: List<StatusElement> = emptyList(),
    val stats: List<StatDelta> = emptyList(),
    val clauses: List<EffectClause> = emptyList(),
    val rules: List<ItemRule> = emptyList(),
    val stacks: Int = when (rarity) {
        COMMON -> 3
        UNCOMMON, RARE -> 2
        EPIC, LEGENDARY -> 1
    },
    val unlock: Int = 1,
    val rebirth: Int = 0,
    val risk: Boolean = false,
    val active: SkillId? = null,
)

private val Drafts: List<ItemDraft> = listOf(
    // RAM: collisions, mass and raw momentum.
    ItemDraft("Ballast Core", "Lead in the heart of the Core turns every collision into a shove.", COMMON, listOf(RAM),
        stats = listOf(stat(ItemEffect.MASS, 0.12f), stat(ItemEffect.IMPACT_DAMAGE, 0.06f)),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Burst(80f, DamageBasis.HIT, 0.25f),
            given = listOf(EffectCondition.SpeedAtLeast(800f))))),
    ItemDraft("Shock Plate", "A reactive plate that answers every collision with a wave.", UNCOMMON, listOf(RAM, ANCHOR),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Push(140f, 260f),
            EffectAction.Burst(140f, DamageBasis.IMPACT, 0.2f), cooldown = 0.8f)), unlock = 4),
    ItemDraft("Wrecking Momentum", "Every wreck you cause feeds the next one.", UNCOMMON, listOf(RAM, CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Buff(BuffStat.IMPACT, 0.3f, 2.5f, maxStacks = 3),
            given = listOf(EffectCondition.SourceImpact))), unlock = 6),
    ItemDraft("Kinetic Lance", "At true speed the Core stops being a ball and becomes a spear.", RARE, listOf(RAM, PRECISION),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Chain(1, 260f, DamageBasis.HIT, 0.6f),
            given = listOf(EffectCondition.SpeedAtLeast(1_200f)))),
        rules = listOf(ItemRule.AlwaysCrit(EffectCondition.SpeedAtLeast(1_200f))), unlock = 10),
    ItemDraft("Tungsten Heart", "Unbearably heavy. Unstoppable once moving.", EPIC, listOf(RAM),
        stats = listOf(stat(ItemEffect.MASS, 0.45f), stat(ItemEffect.IMPACT_DAMAGE, 0.25f),
            stat(ItemEffect.DASH_POWER, -0.2f)), unlock = 14, risk = true),

    // DRIVE: speed, Dash and the line you carve.
    ItemDraft("Afterburner", "The exhaust of a Dash lingers in the weapon.", COMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Buff(BuffStat.DAMAGE, 0.16f, 1.6f)))),
    ItemDraft("Phase Edge", "A Dash that passes through an enemy leaves a cut behind.", COMMON, listOf(DRIVE, RAM),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.Strike(DamageBasis.IMPACT, 0.45f))), unlock = 2),
    ItemDraft("Slipstream Fins", "Air rushing over the hull carries the heat away.", UNCOMMON, listOf(DRIVE, OVERCLOCK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_100f), BuffStat.COOLING, 0.6f)), unlock = 5),
    ItemDraft("Vector Coil", "Every Dash discharges the field it tore through.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.DASH, apply(StatusElement.ION, 2, 150f))), unlock = 7),
    ItemDraft("Afterimage", "For a moment after a Dash, every weak point is visible.", RARE, listOf(DRIVE, PRECISION),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Buff(BuffStat.CRIT_CHANCE, 0.45f, 1f))), unlock = 11),
    ItemDraft("Redline Governor", "Rewards speed and punishes hesitation in equal measure.", EPIC, listOf(DRIVE),
        rules = listOf(
            ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_400f), BuffStat.DAMAGE, 0.35f),
            ItemRule.WhileModifier(EffectCondition.SpeedBelow(500f), BuffStat.DAMAGE_TAKEN, 0.3f),
        ), unlock = 15, risk = true),

    // ANCHOR: Brake, control and the art of stopping.
    ItemDraft("Brake Caliper", "The momentum you shed has to go somewhere.", COMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Push(180f, 300f),
            EffectAction.Burst(180f, DamageBasis.BRAKE, 0.35f)))),
    ItemDraft("Grav Hook", "Stopping hard drags the world in after you.", UNCOMMON, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Pull(260f, 320f), apply(StatusElement.GRAVITIC, 2, 260f))),
        unlock = 5),
    ItemDraft("Inertial Damper", "Braced and braking, the Core shrugs hits off.", UNCOMMON, listOf(ANCHOR, BULWARK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.4f)), unlock = 6),
    ItemDraft("Stillpoint", "At the center of a stop, the air itself freezes.", RARE, listOf(ANCHOR), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, apply(StatusElement.CRYO, 3, 220f))),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.COOLING, 1f)), unlock = 10),
    ItemDraft("Arrest Engine", "A full stop, released as a full blast.", EPIC, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Burst(240f, DamageBasis.BRAKE, 0.9f),
            EffectAction.Vent(30f), cooldown = 2f)), unlock = 14),

    // ARSENAL: the weapon system and its cadence.
    ItemDraft("Calibrated Coil", "A cleaner signal into the weapon.", COMMON, listOf(ARSENAL),
        stats = listOf(stat(ItemEffect.WEAPON_POWER, 0.08f)),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.Strike(DamageBasis.HIT, 0.6f), every = 6))),
    ItemDraft("Hair Trigger", "Every kill shaves a moment off the next shot.", UNCOMMON, listOf(ARSENAL, CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.RefreshWeapon(0.18f), cooldown = 0.5f)), unlock = 4),
    ItemDraft("Elemental Rounds", "The weapon learns whatever your build breathes.", UNCOMMON, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, applyPick(ElementPick.DOMINANT, 1), every = 4)), unlock = 6),
    ItemDraft("Payload Splitter", "A critical payload splits and seeks a second target.", RARE, listOf(ARSENAL, PRECISION),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Chain(1, 380f, DamageBasis.HIT, 0.5f),
            given = listOf(EffectCondition.SourceWeapon))), unlock = 9),
    ItemDraft("Overcharged Capacitor", "More power than the cooling loop was ever rated for.", EPIC, listOf(ARSENAL, OVERCLOCK),
        stats = listOf(stat(ItemEffect.WEAPON_POWER, 0.35f), stat(ItemEffect.COOLING, -0.25f)), unlock = 13, risk = true),

    // PRECISION: critical hits and weak points.
    ItemDraft("Focus Lens", "A finer read on where things break.", COMMON, listOf(PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_CHANCE, 0.05f)),
        rules = listOf(ItemRule.GuaranteedCritAfter(4))),
    ItemDraft("Weakpoint Scanner", "A status is a crack; the scanner finds it.", UNCOMMON, listOf(PRECISION),
        rules = listOf(ItemRule.CritDamageVersus(EffectCondition.TargetHas(null), 1.2f)), unlock = 4),
    ItemDraft("Hunter's Sigil", "Every precise wound starts to rot.", UNCOMMON, listOf(PRECISION), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.CRIT, apply(StatusElement.ENTROPY, 2))), unlock = 7),
    ItemDraft("Killshot", "A wounded enemy is already finished.", RARE, listOf(PRECISION, CHAIN),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.35f),
            EffectAction.Vent(6f), EffectAction.ExtendCombo(0.4f),
            given = listOf(EffectCondition.TargetBelow(0.35f)), cooldown = 0.5f)), unlock = 11),
    ItemDraft("Glass Edge", "Sharp enough to cut anything, brittle against everything else.", EPIC, listOf(PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_DAMAGE, 0.8f)),
        rules = listOf(ItemRule.NonCriticalDamage(-0.15f)), unlock = 15, risk = true),

    // BULWARK: shields, integrity and recovery.
    ItemDraft("Shield Cell", "A spare charge of hard light.", COMMON, listOf(BULWARK),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 20f)),
        clauses = listOf(on(EffectTrigger.SHIELD_BREAK, EffectAction.Push(160f, 320f)))),
    ItemDraft("Nanite Mesh", "Repair swarms that never sleep.", COMMON, listOf(BULWARK),
        stats = listOf(stat(ItemEffect.REGEN, 0.5f), stat(ItemEffect.MAX_INTEGRITY, 8f))),
    ItemDraft("Reactive Plating", "Hit it and it hits back.", UNCOMMON, listOf(BULWARK, RAM),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.Burst(170f, DamageBasis.FLAT, 40f), cooldown = 1f)), unlock = 5),
    ItemDraft("Capacitor Aegis", "A collapsing shield spills its charge.", UNCOMMON, listOf(BULWARK), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.SHIELD_BREAK, apply(StatusElement.ION, 3, 220f),
            EffectAction.Burst(220f, DamageBasis.SHIELD, 1f))), unlock = 8),
    ItemDraft("Hardlight Weave", "Healing that has nowhere left to go becomes armor.", RARE, listOf(BULWARK),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 10f), stat(ItemEffect.MAX_INTEGRITY, 10f)),
        rules = listOf(ItemRule.Convert(ConversionKind.OVERHEAL_TO_SHIELD, 1.5f)), unlock = 8),
    ItemDraft("Last Stand", "When the Core is nearly gone, it refuses to go.", EPIC, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.Shield(45f),
            EffectAction.Buff(BuffStat.DAMAGE_REDUCTION, 0.4f, 3f),
            given = listOf(EffectCondition.IntegrityBelow(0.3f)), cooldown = 25f)), unlock = 14),

    // OVERCLOCK: heat, cooling and Overdrive.
    ItemDraft("Heat Sink", "Copper fins drink the heat.", COMMON, listOf(OVERCLOCK),
        stats = listOf(stat(ItemEffect.COOLING, 0.2f)),
        rules = listOf(ItemRule.FreeDash(25f, 4f))),
    ItemDraft("Thermal Vent", "Overheating stops being a failure and becomes a weapon.", UNCOMMON, listOf(OVERCLOCK),
        listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.OVERHEAT, EffectAction.Burst(200f, DamageBasis.IMPACT, 0.8f),
            apply(StatusElement.THERMAL, 3, 200f), cooldown = 3f)), unlock = 5),
    ItemDraft("Overdrive Primer", "Overdrive arrives faster and arrives armored.", UNCOMMON, listOf(OVERCLOCK),
        stats = listOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.25f)),
        clauses = listOf(on(EffectTrigger.OVERDRIVE, EffectAction.Shield(25f))), unlock = 5),
    ItemDraft("Reactor Spike", "Overdrive opens with a lightning storm.", RARE, listOf(OVERCLOCK, ARSENAL),
        clauses = listOf(on(EffectTrigger.OVERDRIVE, EffectAction.Vent(100f),
            EffectAction.Chain(5, 520f, DamageBasis.WEAPON, 1.5f))), unlock = 10),
    ItemDraft("Redline Reactor", "Run it hot or do not run it at all.", EPIC, listOf(OVERCLOCK),
        stats = listOf(stat(ItemEffect.COOLING, -0.2f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.HeatAtLeast(60f), BuffStat.DAMAGE, 0.3f)),
        unlock = 13, risk = true),

    // CHAIN: kills, combos and momentum between targets.
    ItemDraft("Combo Battery", "Holds the rhythm a little longer.", COMMON, listOf(CHAIN),
        stats = listOf(stat(ItemEffect.COMBO_WINDOW, 0.8f)),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Vent(15f), every = 10))),
    ItemDraft("Momentum Ledger", "Every kill in the chain is written into the next hit.", UNCOMMON, listOf(CHAIN),
        rules = listOf(ItemRule.ComboDamage(0.01f, 0.3f)), unlock = 4),
    ItemDraft("Chain Detonation", "The fallen do not leave quietly.", UNCOMMON, listOf(CHAIN, RAM),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Burst(100f, DamageBasis.VICTIM_MAX_HP, 0.3f))), unlock = 6),
    ItemDraft("Killstreak Engine", "A long chain spins the reactor up.", RARE, listOf(CHAIN, OVERCLOCK),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Overdrive(10f),
            given = listOf(EffectCondition.ComboAtLeast(6)), cooldown = 0.5f)), unlock = 9),
    ItemDraft("Domino Protocol", "Every second fall knocks five more over.", EPIC, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Chain(5, 360f, DamageBasis.VICTIM_MAX_HP, 0.5f), every = 2)),
        unlock = 13),

    // FORTUNE: Data, Matter, luck and choice.
    ItemDraft("Magnet Lens", "Pulls loose Data from farther away.", COMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 90f), stat(ItemEffect.DATA_GAIN, 0.08f))),
    ItemDraft("Lucky Coin", "Flip it once more.", UNCOMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.LUCK, 0.3f)),
        rules = listOf(ItemRule.BonusRerolls(1)), unlock = 3),
    ItemDraft("Data Siphon", "Elites carry archives; this opens them.", UNCOMMON, listOf(FORTUNE, CHAIN),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.12f)),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.GainData(40f), given = listOf(EffectCondition.TargetElite))),
        unlock = 6),
    ItemDraft("Jackpot Relay", "Every so often the odds pay out, and luck makes it sooner.", RARE, listOf(FORTUNE, CHAIN),
        stats = listOf(stat(ItemEffect.MATTER_GAIN, 0.1f)),
        clauses = listOf(EffectClause(EffectTrigger.KILL, actions = listOf(
            EffectAction.Burst(150f, DamageBasis.VICTIM_MAX_HP, 0.4f), EffectAction.GainData(3f),
        ).toImmutableList(), everyNth = 8, luckShortened = true)), unlock = 8),
    ItemDraft("Gambler's Ledger", "One more card on the table, one less margin for error.", EPIC, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.MAX_INTEGRITY, -15f)),
        rules = listOf(ItemRule.OfferCards(1)), unlock = 12, risk = true),

    // THERMAL.
    ItemDraft("Igniter", "Sparks fly from every collision.", COMMON, listOf(OVERCLOCK), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.IMPACT, apply(StatusElement.THERMAL, 2)))),
    ItemDraft("Incendiary Rounds", "The weapon runs hot on purpose.", COMMON, listOf(ARSENAL), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.THERMAL, 1), every = 3)), unlock = 2),
    ItemDraft("Kindling", "Fires that burn longer and hotter.", UNCOMMON, emptyList(), listOf(StatusElement.THERMAL),
        rules = listOf(ItemRule.ElementPotency(StatusElement.THERMAL, 0.4f), ItemRule.StatusDuration(StatusElement.THERMAL, 1.5f)),
        unlock = 5),
    ItemDraft("Flashpoint", "Ignition arrives early and arrives loud.", RARE, emptyList(), listOf(StatusElement.THERMAL),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.THERMAL, 2), ItemRule.BreakPower(StatusElement.THERMAL, 1f)), unlock = 9),
    ItemDraft("Phoenix Coil", "Every Ignite feeds the Core a little fire.", EPIC, listOf(BULWARK), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Heal(3f), EffectAction.Shield(5f),
            given = listOf(EffectCondition.ElementIs(StatusElement.THERMAL)), cooldown = 0.5f)), unlock = 13),
    ItemDraft("Solar Choir", "When one fire sings, the others answer.", LEGENDARY, listOf(CHAIN), listOf(StatusElement.THERMAL),
        rules = listOf(ItemRule.BreakSpread(StatusElement.THERMAL, 3, 140f)), unlock = 16, rebirth = 2),

    // CRYO.
    ItemDraft("Frost Plating", "Strike the Core and feel the cold bite back.", COMMON, listOf(BULWARK), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.HURT, apply(StatusElement.CRYO, 3, 200f)))),
    ItemDraft("Rime Rounds", "Every shot leaves frost behind.", COMMON, listOf(ARSENAL), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.CRYO, 2), every = 2)), unlock = 2),
    ItemDraft("Cold Snap", "A Dash drags winter in its wake.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.DASH, apply(StatusElement.CRYO, 3, 190f))), unlock = 5),
    ItemDraft("Permafrost", "The cold sets faster and holds longer.", RARE, emptyList(), listOf(StatusElement.CRYO),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.CRYO, 2), ItemRule.BreakPower(StatusElement.CRYO, 0.4f),
            ItemRule.StatusDuration(StatusElement.CRYO, 1.5f)), unlock = 9),
    ItemDraft("Shatterglass", "Frozen things only ever break one way.", EPIC, listOf(PRECISION), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Shards(4, DamageBasis.HIT, 0.3f, ElementPick.CRYO),
            given = listOf(EffectCondition.TargetFrozen))),
        rules = listOf(ItemRule.AlwaysCrit(EffectCondition.TargetFrozen)), unlock = 13),
    ItemDraft("Glacial Heart", "Collide with the frozen and the whole field cracks.", LEGENDARY, listOf(RAM), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Burst(160f, DamageBasis.HIT, 1f), apply(StatusElement.CRYO, 3, 160f),
            given = listOf(EffectCondition.TargetFrozen))), unlock = 16, rebirth = 2),

    // ION.
    ItemDraft("Static Brush", "Collisions build a charge on whatever they touch.", COMMON, listOf(DRIVE), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.IMPACT, apply(StatusElement.ION, 2)))),
    ItemDraft("Arc Relay", "Every third shot jumps.", COMMON, listOf(ARSENAL), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.Chain(2, 260f, DamageBasis.HIT, 0.3f, ElementPick.ION),
            every = 3)), unlock = 2),
    ItemDraft("Capacitor Bank", "Stores more, releases sooner.", UNCOMMON, emptyList(), listOf(StatusElement.ION),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.ION, 2), ItemRule.BreakPower(StatusElement.ION, 0.3f)), unlock = 5),
    ItemDraft("Tesla Spur", "A sharp turn throws lightning off the hull.", RARE, listOf(DRIVE), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.Chain(3, 340f, DamageBasis.IMPACT, 0.7f, ElementPick.ION))),
        unlock = 9),
    ItemDraft("Storm Lattice", "Every Overload winds the reactor tighter.", EPIC, listOf(CHAIN), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Overdrive(4f),
            EffectAction.Buff(BuffStat.ACTIVATION, 0.25f, 3f),
            given = listOf(EffectCondition.ElementIs(StatusElement.ION)), cooldown = 1f)), unlock = 13),

    // GRAVITIC.
    ItemDraft("Graviton Seed", "Shots that weigh down whatever they hit.", COMMON, listOf(ARSENAL), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.GRAVITIC, 1), every = 2))),
    ItemDraft("Lodestone", "Heavy things are drawn to heavier things.", COMMON, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.IMPACT, apply(StatusElement.GRAVITIC, 2))), unlock = 2),
    ItemDraft("Tidal Hook", "A turn swings the field and everything in it.", UNCOMMON, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.Pull(240f, 260f), apply(StatusElement.GRAVITIC, 1, 240f),
            cooldown = 1f)), unlock = 5),
    ItemDraft("Crush Depth", "The collapse begins sooner and ends harder.", RARE, emptyList(), listOf(StatusElement.GRAVITIC),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.GRAVITIC, 2), ItemRule.BreakPower(StatusElement.GRAVITIC, 0.5f)), unlock = 9),
    ItemDraft("Dark Star", "Every Collapse leaves a hole in the world.", EPIC, emptyList(), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Well(180f, 1.6f, DamageBasis.FLAT, 20f),
            given = listOf(EffectCondition.ElementIs(StatusElement.GRAVITIC)), cooldown = 0.8f)), unlock = 13),

    // ENTROPY.
    ItemDraft("Rust Spores", "Shots that leave something growing.", COMMON, listOf(ARSENAL), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.ENTROPY, 1), every = 2))),
    ItemDraft("Corrosive Touch", "Whatever you collide with starts to come apart.", COMMON, listOf(RAM), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.IMPACT, apply(StatusElement.ENTROPY, 3))), unlock = 2),
    ItemDraft("Necrotic Lattice", "Rot that eats deeper.", UNCOMMON, emptyList(), listOf(StatusElement.ENTROPY),
        rules = listOf(ItemRule.ElementPotency(StatusElement.ENTROPY, 0.5f)), unlock = 5),
    ItemDraft("Rot Bloom", "The rotting dead seed the living.", RARE, listOf(CHAIN), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.KILL, apply(StatusElement.ENTROPY, 4, 150f),
            given = listOf(EffectCondition.TargetHas(StatusElement.ENTROPY)))), unlock = 9),
    ItemDraft("Necrotic Exchange", "Your repair swarms learn to eat instead of mend.", EPIC, listOf(BULWARK),
        listOf(StatusElement.ENTROPY),
        stats = listOf(stat(ItemEffect.REGEN, 0.6f)),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Heal(3f),
            given = listOf(EffectCondition.ElementIs(StatusElement.ENTROPY)), cooldown = 0.5f)),
        rules = listOf(ItemRule.Convert(ConversionKind.REGENERATION_TO_DECAY, 2f)), unlock = 13, risk = true),

    // Conversions as low-damage enablers.
    ItemDraft("Deadeye Reticle", "Stop hoping for a lucky hit. Make every hit count.", EPIC, listOf(PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_CHANCE, 0.15f)),
        rules = listOf(ItemRule.Convert(ConversionKind.CRIT_CHANCE_TO_DAMAGE, 2.5f)), unlock = 12, risk = true),
    ItemDraft("Detonator Spur", "A Dash through a burning, rotting enemy finishes the job at once.", RARE, listOf(DRIVE),
        listOf(StatusElement.THERMAL, StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.DASH_HIT,
            EffectAction.Detonate(listOf(StatusElement.THERMAL, StatusElement.ENTROPY).toImmutableList(), 2f)))),
    // Cross-element reactions.
    ItemDraft("Catalyst Matrix", "Makes every reaction more violent.", RARE, emptyList(),
        rules = listOf(ItemRule.ReactionPower(0.35f)), unlock = 8),
    ItemDraft("Prismatic Core", "Teaches the weapon your second element.", EPIC, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, applyPick(ElementPick.SECONDARY, 2), every = 5)), unlock = 12),
    ItemDraft("Resonance Fork", "A reaction that rings on after it ends.", LEGENDARY, emptyList(),
        rules = listOf(ItemRule.ReactionResidue(3)), unlock = 16, rebirth = 4),
    ItemDraft("Volatile Compound", "Stronger reactions, and nothing between you and them.", EPIC, listOf(FORTUNE),
        rules = listOf(ItemRule.ReactionPower(0.8f), ItemRule.ReactionRecoil(5f, 140f)), unlock = 14, risk = true),

    // SONIC (ids 80-84). Item ids are positional: new items are only ever appended.
    ItemDraft("Tuning Spike", "Every collision rings like a struck bell.", COMMON, listOf(RAM), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.IMPACT, apply(StatusElement.SONIC, 2)))),
    ItemDraft("Subwoofer Rounds", "Shots that hum long after they land.", COMMON, listOf(ARSENAL), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.SONIC, 1), every = 2)), unlock = 3),
    ItemDraft("Shockwave Hull", "Strike the hull and the whole field hears it.", UNCOMMON, listOf(BULWARK), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.HURT, apply(StatusElement.SONIC, 2, 180f), EffectAction.Push(180f, 320f))), unlock = 5),
    ItemDraft("Sonic Wake", "A Dash leaves a sonic boom in its wake.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.DASH, apply(StatusElement.SONIC, 2, 160f))), unlock = 7),
    ItemDraft("Echo Hammer", "Every Shatter rings out a second time.", UNCOMMON, listOf(RAM), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Burst(140f, DamageBasis.IMPACT, 0.6f),
            given = listOf(EffectCondition.ElementIs(StatusElement.SONIC)), cooldown = 0.6f)), unlock = 9),

    // VOID (ids 85-89).
    ItemDraft("Hollow Point", "Every clean hit leaves an empty space.", COMMON, listOf(PRECISION), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.CRIT, apply(StatusElement.VOID, 3))), unlock = 4),
    ItemDraft("Null Rounds", "Shots that take a little of the target with them.", COMMON, listOf(ARSENAL), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.VOID, 2, 90f), every = 2)), unlock = 2),
    ItemDraft("Event Veil", "A failing shield folds the space around it.", UNCOMMON, listOf(BULWARK), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.SHIELD_BREAK, apply(StatusElement.VOID, 3, 220f))), unlock = 6),
    ItemDraft("Quiet Engine", "The emptiness spreads from the fallen.", UNCOMMON, listOf(CHAIN), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.KILL, apply(StatusElement.VOID, 4, 200f), EffectAction.Burst(200f, DamageBasis.VICTIM_MAX_HP, 0.12f),
            given = listOf(EffectCondition.TargetHas(StatusElement.VOID)))), unlock = 8),
    ItemDraft("Abyssal Lens", "Aim for the hole the void left behind.", UNCOMMON, listOf(PRECISION), listOf(StatusElement.VOID),
        rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetHas(StatusElement.VOID, 3), 0.15f)), unlock = 10),

    // Ids 90-172: the 0.4.0 expansion, grouped by theme. Commons and Uncommons are ungated; Rares, Epics, the six
    // legendaries and Fault Line each wait for a Rebirth tier, four items per tier.
    // SONIC (ids 90-95).
    ItemDraft("Ringing Debris", "The fallen keep ringing long after they break.", COMMON, listOf(CHAIN), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.KILL, apply(StatusElement.SONIC, 2, 150f))), unlock = 3),
    ItemDraft("Bass Drop", "Release the brake and let the floor shake.", COMMON, listOf(ANCHOR), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, apply(StatusElement.SONIC, 3, 210f), EffectAction.Push(210f, 280f))),
        unlock = 5),
    ItemDraft("Harmonic Lens", "Resonating hulls flinch in the same place every time.", UNCOMMON, listOf(PRECISION),
        listOf(StatusElement.SONIC),
        rules = listOf(ItemRule.CritDamageVersus(EffectCondition.TargetHas(StatusElement.SONIC), 1.4f)), unlock = 8),
    ItemDraft("Resonant Turn", "Every hard turn drags a sonic boom around with it.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.TURN, apply(StatusElement.SONIC, 2, 190f))), unlock = 10),
    ItemDraft("Shatter Coil", "Wound so tight that the first crack brings the rest.", RARE, listOf(RAM), listOf(StatusElement.SONIC),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.SONIC, 1), ItemRule.BreakPower(StatusElement.SONIC, 0.6f)), rebirth = 2),
    ItemDraft("Cathedral Bell", "When one bell breaks, the whole tower answers.", LEGENDARY, listOf(CHAIN), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Chain(4, 340f, DamageBasis.FLAT, 30f, ElementPick.SONIC, 2),
            given = listOf(EffectCondition.ElementIs(StatusElement.SONIC)), cooldown = 0.5f)),
        rules = listOf(ItemRule.ElementPotency(StatusElement.SONIC, 0.35f)), rebirth = 6),

    // VOID (ids 96-101).
    ItemDraft("Vacuum Seal", "A Dash through an enemy leaves the shape of you inside it.", COMMON, listOf(DRIVE),
        listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.DASH_HIT, apply(StatusElement.VOID, 3, 140f), EffectAction.Strike(DamageBasis.IMPACT, 0.25f))),
        unlock = 4),
    ItemDraft("Memory Leak", "The emptiness forgets nothing, and forgets it slowly.", UNCOMMON, emptyList(), listOf(StatusElement.VOID),
        rules = listOf(ItemRule.StatusDuration(StatusElement.VOID, 1.6f), ItemRule.ElementPotency(StatusElement.VOID, 0.8f)), unlock = 7),
    ItemDraft("Hollow Harvest", "What the void empties, the shield keeps.", UNCOMMON, listOf(CHAIN, BULWARK), listOf(StatusElement.VOID),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 12f)),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Shield(8f),
            given = listOf(EffectCondition.TargetHas(StatusElement.VOID)), cooldown = 0.5f)), unlock = 9),
    ItemDraft("Unmaking Engine", "It takes less to undo a thing than to make it.", RARE, listOf(ANCHOR), listOf(StatusElement.VOID),
        rules = listOf(ItemRule.BreakThreshold(StatusElement.VOID, 1), ItemRule.BreakPower(StatusElement.VOID, 1.5f),
            ItemRule.BreakSpread(StatusElement.VOID, 2, 160f)), rebirth = 2),
    ItemDraft("Black Mirror", "Strike it and the blow falls into the dark.", RARE, listOf(BULWARK), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.Buff(BuffStat.DAMAGE_REDUCTION, 0.2f, 2f),
            apply(StatusElement.VOID, 3, 230f), cooldown = 1f)), rebirth = 5),
    ItemDraft("Nothing Left", "Every Unmake tears a hole that keeps on taking.", LEGENDARY, listOf(ANCHOR), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.BREAK, EffectAction.Well(220f, 2f, DamageBasis.FLAT, 28f, ElementPick.VOID),
            given = listOf(EffectCondition.ElementIs(StatusElement.VOID)), cooldown = 0.5f)),
        rules = listOf(ItemRule.BreakPower(StatusElement.VOID, 0.4f)), rebirth = 8),

    // RICOCHET and SLAM (ids 102-111): obstacle bounces and enemies thrown into things.
    ItemDraft("Rebound Plating", "Every bounce leaves a little hard light on the hull.", COMMON, listOf(DRIVE, BULWARK),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 12f)),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Shield(12f), cooldown = 0.5f)), unlock = 2),
    ItemDraft("Heat Shunt", "Bouncing off rock dumps the heat into it.", COMMON, listOf(DRIVE, OVERCLOCK),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Vent(20f), cooldown = 0.5f)), unlock = 3),
    ItemDraft("Bank Shot", "Come off the wall harder than you went in.", UNCOMMON, listOf(RAM),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Buff(BuffStat.IMPACT, 0.4f, 3f, maxStacks = 2), cooldown = 0.5f)),
        unlock = 6),
    ItemDraft("Kinetic Recapture", "Momentum lost to the wall is momentum banked.", RARE, listOf(OVERCLOCK, DRIVE),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Overdrive(8f), cooldown = 0.5f)), rebirth = 2),
    ItemDraft("Perfect Carom", "Every angle was planned, and every angle ends in a blast.", LEGENDARY, listOf(DRIVE, RAM),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Burst(240f, DamageBasis.IMPACT, 1.4f), EffectAction.Push(240f, 360f),
            cooldown = 0.5f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(900f), BuffStat.IMPACT, 0.2f)), rebirth = 4),
    ItemDraft("Concussive Rivets", "Thrown enemies ring when they hit something.", COMMON, listOf(RAM), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.SLAM, apply(StatusElement.SONIC, 2), cooldown = 0.5f)), unlock = 4),
    ItemDraft("Splinter Field", "Whatever they hit breaks into something sharp.", UNCOMMON, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.Shards(3, DamageBasis.FLAT, 18f), cooldown = 0.5f)), unlock = 7),
    ItemDraft("Crumple Zone", "Hard landings take a piece of whoever lands.", UNCOMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.06f), cooldown = 0.5f)), unlock = 9),
    ItemDraft("Salvage Collisions", "Wreckage is only Data in the wrong shape.", RARE, listOf(RAM, FORTUNE),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.GainData(2f), every = 3)), rebirth = 4),
    ItemDraft("Pile-Up Protocol", "Slam a burning, rotting thing and it all goes at once.", EPIC, listOf(CHAIN),
        listOf(StatusElement.THERMAL, StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.SLAM,
            EffectAction.Detonate(listOf(StatusElement.THERMAL, StatusElement.ENTROPY).toImmutableList(), 1.8f), cooldown = 0.5f)),
        rebirth = 6),

    // WEAPON SUPPORT (ids 112-123): attack speed, weapon power, infusions and refunds.
    ItemDraft("Servo Trigger", "A lighter pull and a faster weapon.", COMMON, listOf(ARSENAL),
        stats = listOf(stat(ItemEffect.ATTACK_SPEED, 0.1f))),
    ItemDraft("Napalm Feed", "Every fourth shot splashes fire around its target.", COMMON, listOf(ARSENAL), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.THERMAL, 2, 100f), every = 4)), unlock = 3),
    ItemDraft("Blight Cartridge", "Each magazine carries a little rot.", COMMON, listOf(ARSENAL), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.ENTROPY, 3, 70f), every = 4)), unlock = 5),
    ItemDraft("Gyro Stabilizer", "Brace the Core and the weapon steadies with it.", UNCOMMON, listOf(ARSENAL, ANCHOR),
        stats = listOf(stat(ItemEffect.ATTACK_SPEED, 0.12f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.WEAPON, 0.25f)), unlock = 6),
    ItemDraft("Focusing Array", "Tighter beams, cleaner hits.", UNCOMMON, listOf(ARSENAL, PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_CHANCE, 0.03f), stat(ItemEffect.WEAPON_POWER, 0.12f)), unlock = 8),
    ItemDraft("Cryo Feed", "Every third shot frosts the air around its target.", UNCOMMON, listOf(ARSENAL), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.CRYO, 2, 110f), every = 3)), unlock = 11),
    ItemDraft("Autoloader", "A kill by the weapon slams the next round home.", UNCOMMON, listOf(CHAIN, ARSENAL),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.RefreshWeapon(0.3f),
            given = listOf(EffectCondition.SourceWeapon), cooldown = 0.5f)), unlock = 13),
    ItemDraft("Hyperfeed Rails", "Feeds faster than the barrel can cool, so the barrel learns.", RARE, listOf(ARSENAL, OVERCLOCK),
        stats = listOf(stat(ItemEffect.ATTACK_SPEED, 0.18f)),
        clauses = listOf(on(EffectTrigger.OVERHEAT, EffectAction.Vent(40f), cooldown = 3f)), rebirth = 1),
    ItemDraft("Siege Capacitor", "Stores a critical hit's charge for the shots that follow.", RARE, listOf(ARSENAL, PRECISION),
        stats = listOf(stat(ItemEffect.WEAPON_POWER, 0.18f)),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Buff(BuffStat.WEAPON, 0.25f, 2.5f),
            given = listOf(EffectCondition.SourceWeapon))), rebirth = 4),
    ItemDraft("Storm Breech", "Every second shot leaves its target crackling.", RARE, listOf(ARSENAL), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.ION, 2), every = 2)),
        rules = listOf(ItemRule.ElementPotency(StatusElement.ION, 0.3f)), rebirth = 6),
    ItemDraft("Gravity Breech", "Every third shot drops a knot of weight around its target.", EPIC, listOf(ARSENAL, ANCHOR),
        listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, apply(StatusElement.GRAVITIC, 2, 150f), EffectAction.Pull(150f, 220f), every = 3)),
        rules = listOf(ItemRule.BreakPower(StatusElement.GRAVITIC, 0.5f)), rebirth = 5),
    ItemDraft("Pyre Magazine", "Rounds that turn slow fire and slow rot into one fast end.", LEGENDARY, listOf(ARSENAL),
        listOf(StatusElement.THERMAL, StatusElement.ENTROPY),
        clauses = listOf(
            on(EffectTrigger.WEAPON_HIT,
                EffectAction.Detonate(listOf(StatusElement.THERMAL, StatusElement.ENTROPY).toImmutableList(), 1.5f), every = 5),
            on(EffectTrigger.WEAPON_HIT, apply(StatusElement.THERMAL, 1), apply(StatusElement.ENTROPY, 1), every = 2),
        ), rebirth = 5),

    // NEW REACTIONS (ids 124-134): one item for each reaction of Sonic and Void.
    ItemDraft("Stormcaller Plate", "Fire and sound make thunder; the plate makes it louder.", COMMON, emptyList(),
        listOf(StatusElement.THERMAL, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Burst(160f, DamageBasis.FLAT, 30f),
            given = pair(StatusElement.THERMAL, StatusElement.SONIC))), unlock = 4),
    ItemDraft("Glass Harmonics", "Frozen things sing at a pitch that splits them.", UNCOMMON, emptyList(),
        listOf(StatusElement.CRYO, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Shards(4, DamageBasis.FLAT, 18f, ElementPick.CRYO),
            given = pair(StatusElement.CRYO, StatusElement.SONIC))), unlock = 7),
    ItemDraft("Arc Tuner", "Tune the current to the hum and it jumps farther.", UNCOMMON, emptyList(),
        listOf(StatusElement.ION, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Chain(3, 300f, DamageBasis.FLAT, 22f, ElementPick.ION),
            given = pair(StatusElement.ION, StatusElement.SONIC))), unlock = 14),
    ItemDraft("Fault Line", "Rot follows the cracks the sound leaves behind.", UNCOMMON, emptyList(),
        listOf(StatusElement.ENTROPY, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.REACTION, apply(StatusElement.ENTROPY, 2, 160f),
            given = pair(StatusElement.ENTROPY, StatusElement.SONIC))), rebirth = 10),
    ItemDraft("Tectonic Driver", "Weight and sound together move the ground itself.", RARE, emptyList(),
        listOf(StatusElement.GRAVITIC, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Pull(230f, 320f), apply(StatusElement.GRAVITIC, 2, 230f),
            given = pair(StatusElement.GRAVITIC, StatusElement.SONIC), cooldown = 0.8f)), rebirth = 3),
    ItemDraft("Quiet Ward", "In the silence after, nothing can touch you.", RARE, emptyList(),
        listOf(StatusElement.SONIC, StatusElement.VOID),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 15f)),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Shield(14f),
            given = pair(StatusElement.SONIC, StatusElement.VOID), cooldown = 1f)), rebirth = 7),
    ItemDraft("Blackfire Censer", "A flame that burns what is not there.", RARE, emptyList(),
        listOf(StatusElement.THERMAL, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.06f),
            given = pair(StatusElement.THERMAL, StatusElement.VOID))), rebirth = 5),
    ItemDraft("Frozen Moment", "Cold and nothing stop the clock, and you step out of it.", EPIC, emptyList(),
        listOf(StatusElement.CRYO, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Buff(BuffStat.DAMAGE, 0.35f, 4f), EffectAction.Vent(25f),
            given = pair(StatusElement.CRYO, StatusElement.VOID), cooldown = 1.5f)), rebirth = 8),
    ItemDraft("Annihilator Coil", "Charge meets nothing, and something else disappears.", EPIC, emptyList(),
        listOf(StatusElement.ION, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Overdrive(6f),
            EffectAction.Chain(2, 300f, DamageBasis.VICTIM_MAX_HP, 0.08f),
            given = pair(StatusElement.ION, StatusElement.VOID), cooldown = 0.8f)), rebirth = 10),
    ItemDraft("Singularity Seed", "Weight falls into nothing and keeps on falling.", EPIC, emptyList(),
        listOf(StatusElement.GRAVITIC, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.Well(200f, 2f, DamageBasis.FLAT, 25f, ElementPick.VOID),
            given = pair(StatusElement.GRAVITIC, StatusElement.VOID), cooldown = 1f)), rebirth = 10),
    ItemDraft("Last Word", "Rot and void agree on one thing: an ending.", LEGENDARY, emptyList(),
        listOf(StatusElement.ENTROPY, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION,
            EffectAction.Chain(5, 360f, DamageBasis.VICTIM_MAX_HP, 0.1f, ElementPick.VOID, 2),
            given = pair(StatusElement.ENTROPY, StatusElement.VOID), cooldown = 0.5f)),
        rules = listOf(ItemRule.ReactionPower(0.3f)), rebirth = 10),

    // FORTUNE (ids 135-140).
    ItemDraft("Salvage Rig", "Strips a little more from every wreck.", COMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.MATTER_GAIN, 0.15f)), unlock = 2),
    ItemDraft("Data Prospector", "Every few fragments, a richer seam.", COMMON, listOf(FORTUNE),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.GainData(2.5f), every = 5)), unlock = 5),
    ItemDraft("Windfall Sensor", "Reads the odds a moment before they land.", UNCOMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 40f), stat(ItemEffect.LUCK, 0.3f), stat(ItemEffect.DATA_GAIN, 0.12f)), unlock = 6),
    ItemDraft("Second Look", "Not this hand. Deal again.", UNCOMMON, listOf(FORTUNE),
        rules = listOf(ItemRule.BonusRerolls(2)), unlock = 16),
    ItemDraft("Compound Interest", "Every level pays a dividend toward the next.", RARE, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.1f)),
        clauses = listOf(on(EffectTrigger.LEVEL_UP, EffectAction.GainData(8f))), rebirth = 7),
    ItemDraft("Cornucopia Core", "There is always one more choice on the table.", LEGENDARY, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.LUCK, 0.4f), stat(ItemEffect.DATA_GAIN, 0.12f)),
        rules = listOf(ItemRule.OfferCards(1)), rebirth = 3),

    // ANCHOR (ids 141-144).
    ItemDraft("Drag Chute", "Stopping hard primes the next strike.", COMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Buff(BuffStat.DAMAGE, 0.25f, 2.5f))), unlock = 2),
    ItemDraft("Anchor Bolt", "Heavier, steadier and harder to hurt.", COMMON, listOf(ANCHOR, BULWARK),
        stats = listOf(stat(ItemEffect.DAMAGE_REDUCTION, 0.08f), stat(ItemEffect.MASS, 0.1f)), unlock = 4),
    ItemDraft("Stopping Power", "Everything you were carrying keeps going without you.", UNCOMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Chain(3, 320f, DamageBasis.BRAKE, 0.5f))), unlock = 8),
    ItemDraft("Dead Stop", "Stop dead and the world falls toward you.", RARE, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Well(200f, 1.5f, DamageBasis.BRAKE, 0.35f))), rebirth = 1),

    // OVERCLOCK (ids 145-148).
    ItemDraft("Coolant Loop", "Runs cold and recovers fast.", COMMON, listOf(OVERCLOCK),
        stats = listOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.1f), stat(ItemEffect.COOLING, 0.15f)), unlock = 3),
    ItemDraft("Thermal Regulator", "Heat in the system turns into tempo.", UNCOMMON, listOf(OVERCLOCK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.HeatAtLeast(40f), BuffStat.ACTIVATION, 0.3f)), unlock = 7),
    ItemDraft("Afterburn Injector", "Overdrive ignites with a blast of its own.", UNCOMMON, listOf(OVERCLOCK),
        clauses = listOf(on(EffectTrigger.OVERDRIVE, EffectAction.Burst(260f, DamageBasis.WEAPON, 2f))), unlock = 12),
    ItemDraft("Redline Rush", "Overheating is only the engine asking for more.", RARE, listOf(OVERCLOCK),
        clauses = listOf(on(EffectTrigger.OVERHEAT, EffectAction.Buff(BuffStat.DAMAGE, 0.35f, 4f), cooldown = 2f)), rebirth = 1),

    // BULWARK (ids 149-152).
    ItemDraft("Hull Brace", "Extra ribs where the hits land.", COMMON, listOf(RAM, BULWARK),
        stats = listOf(stat(ItemEffect.MAX_INTEGRITY, 15f)), unlock = 3),
    ItemDraft("Momentum Shield", "A Dash spins up a sliver of hard light.", UNCOMMON, listOf(DRIVE, BULWARK),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 12f)),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Shield(8f), cooldown = 1f)), unlock = 6),
    ItemDraft("Fortress Protocol", "The closer it comes to failing, the harder it holds.", RARE, listOf(ANCHOR, BULWARK),
        stats = listOf(stat(ItemEffect.MAX_INTEGRITY, 15f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.IntegrityBelow(0.6f), BuffStat.DAMAGE_TAKEN, -0.3f)), rebirth = 1),
    ItemDraft("Aegis Feedback", "A broken shield gives some of itself back.", RARE, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.SHIELD_BREAK, EffectAction.Push(200f, 340f), EffectAction.Heal(10f), cooldown = 4f)),
        rebirth = 3),

    // PRECISION (ids 153-156).
    ItemDraft("Tracer Optics", "See the weak point a frame earlier.", COMMON, listOf(PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_DAMAGE, 0.2f), stat(ItemEffect.CRIT_CHANCE, 0.1f)), unlock = 2),
    ItemDraft("Spotter Mark", "Every critical hit marks the seam for one more blow.", UNCOMMON, listOf(PRECISION),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.04f), cooldown = 0.5f)), unlock = 7),
    ItemDraft("Executioner's Scope", "A wounded target is an easy one.", RARE, listOf(PRECISION),
        rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetBelow(0.4f), 0.35f)), rebirth = 3),
    ItemDraft("Stillness Sight", "Slow down and every shot finds the seam.", EPIC, listOf(PRECISION, ANCHOR),
        stats = listOf(stat(ItemEffect.CRIT_DAMAGE, 0.25f)),
        rules = listOf(ItemRule.AlwaysCrit(EffectCondition.SpeedBelow(350f))), rebirth = 7),

    // CHAIN (ids 157-160).
    ItemDraft("Momentum Battery", "Every kill winds the spring a little tighter.", COMMON, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Buff(BuffStat.DAMAGE, 0.12f, 3f, maxStacks = 3))), unlock = 4),
    ItemDraft("Shrapnel Carcass", "The fallen burst into pieces that keep on cutting.", UNCOMMON, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Shards(3, DamageBasis.VICTIM_MAX_HP, 0.15f))), unlock = 15),
    ItemDraft("Vortex Ledger", "A long enough chain bends space around the last kill.", RARE, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Well(180f, 1.5f, DamageBasis.VICTIM_MAX_HP, 0.12f),
            given = listOf(EffectCondition.ComboAtLeast(5)), cooldown = 1.5f)), rebirth = 6),
    ItemDraft("Avalanche Protocol", "Every third fall drags the rest down with it.", EPIC, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Pull(220f, 320f), EffectAction.Burst(220f, DamageBasis.VICTIM_MAX_HP, 0.25f),
            every = 3)), rebirth = 8),

    // DRIVE (ids 161-164).
    ItemDraft("Slipstream Burst", "A Dash cracks the air around the hull.", COMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Burst(150f, DamageBasis.IMPACT, 0.3f))), unlock = 3),
    ItemDraft("Vector Lock", "Lock the turn and lock the target.", UNCOMMON, listOf(DRIVE, PRECISION),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.Buff(BuffStat.CRIT_CHANCE, 0.3f, 1.5f))), unlock = 6),
    ItemDraft("Phantom Wake", "A Dash hit splits and finds two more.", RARE, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.Chain(2, 300f, DamageBasis.HIT, 0.6f))), rebirth = 2),
    ItemDraft("Comet Drive", "Faster than anything should be, and harder to stop.", EPIC, listOf(DRIVE, RAM),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.25f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_100f), BuffStat.IMPACT, 0.45f)), rebirth = 9),

    // RAM (ids 165-168).
    ItemDraft("Crash Bars", "Reinforced edges for meeting things head-on.", COMMON, listOf(RAM),
        stats = listOf(stat(ItemEffect.IMPACT_DAMAGE, 0.1f), stat(ItemEffect.MASS, 0.06f))),
    ItemDraft("Shrapnel Ram", "Every collision throws off shards.", UNCOMMON, listOf(RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Shards(3, DamageBasis.HIT, 0.35f), cooldown = 0.3f)), unlock = 5),
    ItemDraft("Wrecking Ball", "Big hits take big pieces.", RARE, listOf(RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Strike(DamageBasis.VICTIM_MAX_HP, 0.05f),
            given = listOf(EffectCondition.SpeedAtLeast(600f)), cooldown = 0.4f)), rebirth = 4),
    ItemDraft("Siege Ram", "Built to break walls; enemies are easier.", EPIC, listOf(RAM, ANCHOR),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.Burst(180f, DamageBasis.HIT, 0.8f), EffectAction.Push(180f, 300f),
            given = listOf(EffectCondition.SpeedAtLeast(900f)), cooldown = 0.6f)), rebirth = 9),

    // RISK (ids 169-172): Epics with a liability in the Volatile lane.
    ItemDraft("Nitro Injector", "All the thrust you could want, and all the heat that comes with it.", EPIC, listOf(DRIVE),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.4f), stat(ItemEffect.DASH_EFFICIENCY, -0.3f)),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.Strike(DamageBasis.IMPACT, 0.8f))), rebirth = 7, risk = true),
    ItemDraft("Hard Stop", "Hit harder from a standstill and pay for it at speed.", EPIC, listOf(ANCHOR),
        rules = listOf(
            ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE, 0.5f),
            ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_000f), BuffStat.DAMAGE_TAKEN, 0.25f),
        ), rebirth = 8, risk = true),
    ItemDraft("Usurer's Contract", "Borrow against your hull; the interest is paid in Data.", EPIC, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.35f), stat(ItemEffect.MATTER_GAIN, 0.25f), stat(ItemEffect.MAX_INTEGRITY, -20f)),
        rebirth = 9, risk = true),
    ItemDraft("Feeding Frenzy", "Kills feed you. Elites are harder to bite.", EPIC, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Heal(3f), EffectAction.Buff(BuffStat.DAMAGE, 0.15f, 2.5f, maxStacks = 2),
            cooldown = 0.5f)),
        rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetElite, -0.25f)), rebirth = 9, risk = true),

    // 0.5.0 (ids 173 onward): items built on skills, the Apex, reaction keystones, fields, marks and the Core's
    // movement, appended in themed batches of twelve.
    // CASTING (SKILL BUILDS) (ids 173-184).
    ItemDraft("Spark Gap", "After every active the air keeps crackling with charge.", COMMON, listOf(CHAIN), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, apply(StatusElement.ION, 2, 160f))),
        unlock = 3),
    ItemDraft("Kiln Primer", "Whatever your actives touch comes out of the kiln a little hotter.", COMMON, listOf(OVERCLOCK),
        listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, apply(StatusElement.THERMAL, 1))),
        unlock = 2),
    ItemDraft("Launch Rail", "A moving active leaves the rail greased for the next Dash.", COMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.PrimeDash, EffectAction.Buff(BuffStat.DAMAGE, 0.3f, 3f),
            given = listOf(EffectCondition.SkillTagged(DRIVE)))),
        unlock = 4),
    ItemDraft("Echo Capacitor", "Stores the ring of the last cast to start the next one sooner.", COMMON, listOf(OVERCLOCK),
        rules = listOf(ItemRule.SkillCooldown(null, 0.15f)),
        unlock = 4),
    ItemDraft("Overcharge Glyph", "A cast spills its surplus into the weapon.", UNCOMMON, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.Buff(BuffStat.ACTIVATION, 0.3f, 3f))),
        unlock = 5),
    ItemDraft("Cascade Relay", "An active that lands keeps looking for the next target.", RARE, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, EffectAction.Chain(2, 280f, DamageBasis.HIT, 0.5f), every = 3)),
        unlock = 7),
    ItemDraft("Finishing Cadence", "Finish the wounded and the rhythm comes round again.", RARE, listOf(PRECISION),
        clauses = listOf(on(EffectTrigger.SKILL_HIT, EffectAction.RefundSkills(0.4f, SkillPick.CAST),
            given = listOf(EffectCondition.TargetBelow(0.3f)), cooldown = 0.5f)),
        unlock = 6),
    ItemDraft("Catalytic Converter", "Every reaction pays its toll back into your actives.", RARE, listOf(OVERCLOCK, CHAIN),
        rules = listOf(ItemRule.CatalysisRate(0.5f)),
        unlock = 6),
    ItemDraft("Empty Chamber", "With nothing left to cast, there is only the fight.", RARE, listOf(OVERCLOCK),
        rules = listOf(
            ItemRule.WhileModifier(EffectCondition.SkillsCharging(3), BuffStat.DAMAGE, 0.2f),
            ItemRule.WhileModifier(EffectCondition.SkillsCharging(3), BuffStat.COOLING, 0.4f),
        ),
        unlock = 9),
    ItemDraft("Twin Chamber", "Load every active twice and fire them back to back.", EPIC, listOf(ARSENAL),
        rules = listOf(ItemRule.SkillCharges),
        rebirth = 1),
    ItemDraft("Alchemical Core", "Your actives forget their own element and learn yours.", EPIC, listOf(CHAIN),
        rules = listOf(ItemRule.SkillAttunement(ElementPick.DOMINANT), ItemRule.SkillPower(null, 0.2f)),
        unlock = 10),
    ItemDraft("Nomad Field", "The fields you raise pack up and follow you.", RARE, listOf(ANCHOR),
        rules = listOf(ItemRule.FieldTuning(FieldAspect.DURATION, 0.2f), ItemRule.Convert(ConversionKind.SKILL_FIELDS_FOLLOW, 0.8f)),
        unlock = 8),

    // ARCHIVE AND APEX (RANK-0 DATA, LEVEL 42) (ids 185-196).
    ItemDraft("Tithe Collector", "Every tenth fragment goes to the reactor.", COMMON, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.OVERDRIVE_GAIN, 0.1f), stat(ItemEffect.DATA_GAIN, 0.05f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Overdrive(4f), every = 10))),
    ItemDraft("Mycelial Ledger", "Rot runs through the dead like roots, and the roots carry Data.", COMMON, listOf(FORTUNE),
        listOf(StatusElement.ENTROPY),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.02f)),
        clauses = listOf(on(EffectTrigger.KILL, apply(StatusElement.ENTROPY, 2, 130f), EffectAction.GainData(0.3f),
            given = listOf(EffectCondition.TargetHas(StatusElement.ENTROPY, 3)), cooldown = 0.25f)),
        unlock = 3),
    ItemDraft("Scavenger Protocol", "A held enemy cannot hide what it carries.", RARE, listOf(FORTUNE, ANCHOR), listOf(StatusElement.CRYO),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.035f)),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.GainData(0.5f), given = listOf(EffectCondition.TargetControlled), cooldown = 0.3f)),
        unlock = 4),
    ItemDraft("Bit Furnace", "Burn the spare bits to cool the hull.", UNCOMMON, listOf(FORTUNE, OVERCLOCK),
        stats = listOf(stat(ItemEffect.COOLING, 0.1f), stat(ItemEffect.DATA_GAIN, 0.06f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Vent(10f), every = 6)),
        unlock = 5),
    ItemDraft("Rote Memory", "Repetition is how an active gets faster.", RARE, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.LUCK, 0.2f), stat(ItemEffect.DATA_GAIN, 0.05f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.RefundSkills(0.3f, SkillPick.LONGEST), every = 8)),
        unlock = 6),
    ItemDraft("Lodestar Ledger", "Every page of Data is bound in hard light.", RARE, listOf(BULWARK, FORTUNE),
        stats = listOf(stat(ItemEffect.DATA_GAIN, 0.04f), stat(ItemEffect.SHIELD_CAPACITY, 10f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.Shield(5f), every = 8)),
        unlock = 7),
    ItemDraft("Debt Collector", "A cashed mark always pays out.", RARE, listOf(FORTUNE, PRECISION),
        stats = listOf(stat(ItemEffect.CRIT_DAMAGE, 0.15f), stat(ItemEffect.DATA_GAIN, 0.1f)),
        clauses = listOf(on(EffectTrigger.MARK_CONSUMED, EffectAction.GainData(2.5f))),
        unlock = 8),
    ItemDraft("Summit Battery", "Charged on the climb, spent at the top.", RARE, listOf(OVERCLOCK),
        clauses = listOf(on(EffectTrigger.LEVEL_UP, EffectAction.RefundSkills(1f, SkillPick.ALL))),
        rules = listOf(ItemRule.ApexCooldown(0.15f)),
        unlock = 26),
    ItemDraft("Zenith Ledger", "Every fragment past the summit feeds the crown.", RARE, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 40f)),
        clauses = listOf(on(EffectTrigger.PICKUP, EffectAction.ChargeApex(1f), every = 15)),
        unlock = 24),
    ItemDraft("Crest Rider", "Ride the crest of the wave you built.", UNCOMMON, listOf(DRIVE),
        clauses = listOf(
            on(EffectTrigger.DASH_HIT, EffectAction.ChargeApex(0.8f), every = 4),
            on(EffectTrigger.APEX, EffectAction.CoreImpulse(500f, ImpulseDirection.POINTER), EffectAction.Buff(BuffStat.DAMAGE, 0.25f, 10f)),
        ),
        unlock = 22),
    ItemDraft("Overture", "The Apex opens, and every instrument comes in at once.", EPIC, listOf(OVERCLOCK),
        clauses = listOf(
            on(EffectTrigger.APEX, EffectAction.RefundSkills(3f, SkillPick.ALL), EffectAction.Overdrive(40f)),
            on(EffectTrigger.SKILL_CAST, EffectAction.ChargeApex(2f), every = 4),
        ),
        unlock = 26),
    ItemDraft("Encore Protocol", "The crowd wants it again, louder.", EPIC, listOf(CHAIN),
        clauses = listOf(
            on(EffectTrigger.APEX, EffectAction.ForceReaction(300f, 4)),
            on(EffectTrigger.REACTION, EffectAction.ChargeApex(1.5f), every = 6),
        ),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.ApexActive, BuffStat.DAMAGE, 0.2f)),
        unlock = 28,
        rebirth = 3),

    // KEYSTONES I (REACTIONS 0–11) (ids 197-208).
    ItemDraft("Quench Seal", "Fire meets frost and the steel sets hard.", EPIC, listOf(PRECISION), listOf(StatusElement.THERMAL, StatusElement.CRYO),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.THERMAL_SHOCK), ItemRule.CritDamageVersus(EffectCondition.TargetFrozen, 0.5f)),
        unlock = 10),
    ItemDraft("Arc Furnace", "The plasma never pools; it leaps to the next charged hull.", EPIC, listOf(CHAIN),
        listOf(StatusElement.THERMAL, StatusElement.ION),
        clauses = listOf(on(EffectTrigger.REACTION, apply(StatusElement.ION, 2, 220f),
            given = listOf(EffectCondition.ReactionIs(ReactionId.PLASMA_BURST)))),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.PLASMA_BURST)),
        unlock = 10),
    ItemDraft("Degenerate Matter", "A star too small to burn and too heavy to let go.", EPIC, listOf(ANCHOR),
        listOf(StatusElement.THERMAL, StatusElement.GRAVITIC),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.FUSION), ItemRule.ReactionTuning(ReactionId.FUSION, ReactionAspect.RADIUS, 0.3f)),
        unlock = 12),
    ItemDraft("Ember Treads", "Wherever you roll, the rot catches.", EPIC, listOf(RAM), listOf(StatusElement.THERMAL, StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.DASH_HIT, apply(StatusElement.ENTROPY, 2))),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.WILDFIRE)),
        unlock = 10),
    ItemDraft("Cryogenic Bus", "Cold enough that the current meets no resistance at all.", EPIC, listOf(CHAIN),
        listOf(StatusElement.CRYO, StatusElement.ION),
        rules = listOf(
            ItemRule.ReactionKeystone(ReactionId.SUPERCONDUCT),
            ItemRule.ReactionTuning(ReactionId.SUPERCONDUCT, ReactionAspect.RADIUS, 0.3f),
        ),
        unlock = 12),
    ItemDraft("Glacier Scope", "Point the avalanche where you want it to fall.", EPIC, listOf(PRECISION),
        listOf(StatusElement.CRYO, StatusElement.GRAVITIC),
        rules = listOf(
            ItemRule.ReactionKeystone(ReactionId.GLACIER_CRUSH),
            ItemRule.ReactionTuning(ReactionId.GLACIER_CRUSH, ReactionAspect.RADIUS, 0.4f),
        ),
        unlock = 12),
    ItemDraft("Glassblower's Ram", "Blow through brittle things and they shatter behind you.", EPIC, listOf(RAM),
        listOf(StatusElement.CRYO, StatusElement.ENTROPY),
        rules = listOf(
            ItemRule.ReactionKeystone(ReactionId.EMBRITTLE),
            ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(500f), BuffStat.IMPACT, 0.15f),
        ),
        unlock = 12),
    ItemDraft("Like Poles", "Some things are not meant to touch.", EPIC, listOf(RAM), listOf(StatusElement.ION, StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.SLAM, EffectAction.Burst(120f, DamageBasis.FLAT, 40f), cooldown = 0.5f)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.MAGNETIZE)),
        unlock = 12),
    ItemDraft("Shunt Resistor", "The current skips the weapon and goes straight to your actives.", EPIC, listOf(OVERCLOCK),
        listOf(StatusElement.ION, StatusElement.ENTROPY),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.FEEDBACK), ItemRule.SkillPower(null, 0.15f)),
        unlock = 10),
    ItemDraft("Grave Mulch", "What dies in the pit feeds everything else in it.", EPIC, listOf(CHAIN),
        listOf(StatusElement.GRAVITIC, StatusElement.ENTROPY),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.ROT_WELL), ItemRule.ReactionTuning(ReactionId.ROT_WELL, ReactionAspect.RADIUS, 0.3f)),
        unlock = 12),
    ItemDraft("Blast Rider", "Why Dash when the thunder can carry you?", EPIC, listOf(DRIVE), listOf(StatusElement.THERMAL, StatusElement.SONIC),
        rules = listOf(
            ItemRule.ReactionKeystone(ReactionId.THUNDERCLAP),
            ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(900f), BuffStat.DAMAGE_REDUCTION, 0.2f),
        ),
        unlock = 14),
    ItemDraft("Choir Prism", "Seven shards, one note, and everything ringing breaks.", EPIC, listOf(CHAIN),
        listOf(StatusElement.CRYO, StatusElement.SONIC),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.SHATTERPOINT), ItemRule.ElementPotency(StatusElement.SONIC, 0.3f)),
        rebirth = 1),

    // KEYSTONES II AND MARKS (REACTIONS 12–20) (ids 209-220).
    ItemDraft("Wavefront Rudder", "Steer by the standing wave and it steers the lightning.", EPIC, listOf(DRIVE),
        listOf(StatusElement.ION, StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.TURN, apply(StatusElement.ION, 2, 180f), cooldown = 0.75f)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.RESONANT_ARC)),
        unlock = 14),
    ItemDraft("Fault Hammer", "Strike once and the ground strikes again.", EPIC, listOf(RAM), listOf(StatusElement.GRAVITIC, StatusElement.SONIC),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.SEISMIC), ItemRule.ReactionTuning(ReactionId.SEISMIC, ReactionAspect.RADIUS, 0.3f)),
        rebirth = 2),
    ItemDraft("Sapper's Wedge", "Drive it into the crack and nothing holds its shape.", EPIC, listOf(PRECISION),
        listOf(StatusElement.ENTROPY, StatusElement.SONIC),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.FRACTURE), ItemRule.DamageVersus(EffectCondition.TargetMarked, 0.15f)),
        rebirth = 3),
    ItemDraft("Anechoic Shell", "A pocket of silence that travels with you.", EPIC, listOf(BULWARK), listOf(StatusElement.SONIC, StatusElement.VOID),
        stats = listOf(stat(ItemEffect.SHIELD_CAPACITY, 15f)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.SILENCE)),
        rebirth = 5),
    ItemDraft("Brazier of Ash", "Kept burning in one place, the black flame burns hotter.", EPIC, listOf(OVERCLOCK),
        listOf(StatusElement.THERMAL, StatusElement.VOID),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.BLACKFIRE), ItemRule.StatusDuration(StatusElement.THERMAL, 1.3f)),
        rebirth = 4),
    ItemDraft("Hourglass Relay", "Turn the glass and every shot runs back the way it came.", LEGENDARY, listOf(BULWARK),
        listOf(StatusElement.CRYO, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.SHOT_ERASED, EffectAction.Overdrive(3f), every = 4)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.STASIS)),
        rebirth = 8),
    ItemDraft("Horizon Knife", "Step across the horizon and strike before you arrive.", LEGENDARY, listOf(PRECISION),
        listOf(StatusElement.ION, StatusElement.VOID),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.ANNIHILATE), ItemRule.CritDamageVersus(EffectCondition.TargetMarked, 0.5f)),
        rebirth = 6),
    ItemDraft("Starving Lantern", "It carries its own hunger and eats whatever shines at it.", EPIC, listOf(BULWARK),
        listOf(StatusElement.GRAVITIC, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.SHOT_ERASED, EffectAction.Heal(3f), every = 5)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.NULL_WELL)),
        rebirth = 7),
    ItemDraft("Requiem Engine", "A deeper ending, sung for everything that rots.", LEGENDARY, listOf(CHAIN),
        listOf(StatusElement.ENTROPY, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.ChargeApex(2f),
            given = listOf(EffectCondition.ReactionIs(ReactionId.OBLIVION)), cooldown = 1f)),
        rules = listOf(ItemRule.ReactionKeystone(ReactionId.OBLIVION)),
        rebirth = 9),
    ItemDraft("Breaker Switch", "Throw it on impact, pull it on release.", RARE, listOf(ANCHOR, RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.ApplyMark(MarkPick.CRACKED, 4f), cooldown = 0.4f)),
        unlock = 7),
    ItemDraft("Spore Brand", "A precise wound is the best soil.", RARE, listOf(PRECISION, FORTUNE), listOf(StatusElement.ENTROPY),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.ApplyMark(MarkPick.SPORED, 4f), cooldown = 0.5f)),
        unlock = 8),
    ItemDraft("Glass Brand", "Pass through and leave the target as thin as glass.", RARE, listOf(DRIVE), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.ApplyMark(MarkPick.EMBRITTLED, 2f), cooldown = 0.3f)),
        rebirth = 10),

    // FIELDS AND TERRAIN (ids 221-232).
    ItemDraft("Steam Valve", "Inside a field the valve opens and the heat bleeds out.", UNCOMMON, listOf(OVERCLOCK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.CoreInField, BuffStat.COOLING, 0.4f), ItemRule.FieldTuning(FieldAspect.DURATION, 0.2f)),
        unlock = 5),
    ItemDraft("Plasma Spigot", "Overheat and the excess spills out as a pool to stand in.", RARE, listOf(OVERCLOCK), listOf(StatusElement.THERMAL, StatusElement.ION),
        clauses = listOf(on(EffectTrigger.OVERHEAT, EffectAction.Zone(ZoneKind.PLASMA, 120f, 2.5f), cooldown = 3f)),
        unlock = 8),
    ItemDraft("Hush Pedal", "Ease off the brake and the room goes quiet.", UNCOMMON, listOf(ANCHOR), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.Zone(ZoneKind.HUSH, 160f, 2f), cooldown = 4f)),
        unlock = 6),
    ItemDraft("Borrowed Second", "When the hull is failing, time lends you a moment.", RARE, listOf(BULWARK), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.Zone(ZoneKind.TIME_BUBBLE, 140f, 2f), given = listOf(EffectCondition.IntegrityBelow(0.5f)), cooldown = 12f)),
        unlock = 8),
    ItemDraft("Gaping Ground", "The fallen open mouths in the ground.", RARE, listOf(CHAIN), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.Zone(ZoneKind.MAW, 150f, 3f), every = 8)),
        unlock = 7),
    ItemDraft("Field Marshal", "Step into a field and command everything inside to react.", RARE, listOf(CHAIN),
        clauses = listOf(on(EffectTrigger.FIELD_ENTER, EffectAction.ForceReaction(180f, 2), cooldown = 1.5f)),
        unlock = 10),
    ItemDraft("Surveyor's Stakes", "Stake the field a little wider.", COMMON, listOf(FORTUNE),
        rules = listOf(ItemRule.FieldTuning(FieldAspect.RADIUS, 0.15f)),
        unlock = 3),
    ItemDraft("Tidewater Hull", "Inside a field the hull rides the current instead of the blows.", COMMON, listOf(BULWARK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.CoreInField, BuffStat.DAMAGE_REDUCTION, 0.2f)),
        unlock = 2),
    ItemDraft("Quarry Rebound", "Bounce off the rock and the rock takes the shots for you.", RARE, listOf(DRIVE, BULWARK),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.EraseShots(220f, 3), cooldown = 1.5f)),
        unlock = 6),
    ItemDraft("Long Exposure", "Every reaction lingers a little longer in the frame.", RARE, listOf(OVERCLOCK),
        rules = listOf(ItemRule.ReactionTuning(null, ReactionAspect.DURATION, 0.25f)),
        rebirth = 7),
    ItemDraft("Gravity Loom", "Weaves every pull and throw a little tighter.", RARE, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        stats = listOf(stat(ItemEffect.MASS, 0.1f)),
        rules = listOf(ItemRule.ReactionTuning(null, ReactionAspect.FORCE, 0.3f)),
        rebirth = 2),
    ItemDraft("Pocket Well", "Carry a little gravity and drop it where you Dash.", UNCOMMON, listOf(ANCHOR), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.DASH, EffectAction.Well(140f, 1.2f, DamageBasis.IMPACT, 0.2f, ElementPick.GRAVITIC), cooldown = 1.5f)),
        unlock = 7),

    // CORE MOVEMENT (ids 233-244).
    ItemDraft("Recoil Spring", "Let go of the brake and the spring lets go of you.", COMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.CoreImpulse(350f, ImpulseDirection.POINTER))),
        unlock = 2),
    ItemDraft("Kickback Plate", "Hit something fast and bounce off harder.", COMMON, listOf(RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.CoreImpulse(250f, ImpulseDirection.AWAY_FROM_TARGET), given = listOf(EffectCondition.SpeedAtLeast(700f)), cooldown = 0.5f)),
        unlock = 3),
    ItemDraft("Second Wind Valve", "A few kills and the thrusters breathe again.", UNCOMMON, listOf(DRIVE, CHAIN),
        clauses = listOf(on(EffectTrigger.KILL, EffectAction.PrimeDash, every = 6)),
        unlock = 5),
    ItemDraft("Long Tether", "Even a distant reaction finds its way home to the Core.", RARE, listOf(DRIVE),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.1f)),
        rules = listOf(ItemRule.CoreLinkRadius(160f)),
        unlock = 6),
    ItemDraft("Spin Turbine", "Every sharp turn spins the turbine up.", UNCOMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.CoreImpulse(300f, ImpulseDirection.FORWARD), cooldown = 1f)),
        unlock = 5),
    ItemDraft("Overspeed Clock", "At speed, the clock that counts your actives runs fast.", RARE, listOf(DRIVE, OVERCLOCK),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_100f), BuffStat.SKILL_RECHARGE, 0.3f)),
        unlock = 9),
    ItemDraft("Phase Spurs", "Dash through and kick them into the scenery.", RARE, listOf(RAM, DRIVE),
        clauses = listOf(on(EffectTrigger.DASH_HIT, EffectAction.Throw(600f), cooldown = 0.3f)),
        unlock = 8),
    ItemDraft("Event Saddle", "Cash a mark and ride the recoil.", RARE, listOf(DRIVE), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.MARK_CONSUMED, EffectAction.CoreImpulse(350f, ImpulseDirection.FORWARD), EffectAction.PrimeDash, cooldown = 1f)),
        rebirth = 9),
    ItemDraft("Ice Skates", "Cold lightning makes the floor a rink.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.CRYO, StatusElement.ION),
        clauses = listOf(on(EffectTrigger.DASH, apply(StatusElement.CRYO, 1, 140f))),
        rules = listOf(ItemRule.ReactionTuning(ReactionId.SUPERCONDUCT, ReactionAspect.DURATION, 0.5f)),
        unlock = 7),
    ItemDraft("Clap Timer", "More time to catch the blast.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.THERMAL, StatusElement.SONIC),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.1f)),
        rules = listOf(ItemRule.ReactionTuning(ReactionId.THUNDERCLAP, ReactionAspect.DURATION, 0.5f)),
        unlock = 9),
    ItemDraft("Anchor Lanyard", "The anchor holds longer and lands harder.", RARE, listOf(ANCHOR, DRIVE), listOf(StatusElement.ION, StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.MARK_CONSUMED, EffectAction.Burst(160f, DamageBasis.IMPACT, 0.6f), given = listOf(EffectCondition.ReactionIs(ReactionId.ANNIHILATE)))),
        rules = listOf(ItemRule.ReactionTuning(ReactionId.ANNIHILATE, ReactionAspect.DURATION, 0.5f)),
        rebirth = 1),
    ItemDraft("Gravity Assist", "Swing round the turn and come out faster than you went in.", EPIC, listOf(DRIVE, RAM),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.Burst(160f, DamageBasis.IMPACT, 0.5f), EffectAction.CoreImpulse(450f, ImpulseDirection.FORWARD), given = listOf(EffectCondition.SpeedAtLeast(1_100f)), cooldown = 1f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(1_400f), BuffStat.DAMAGE_REDUCTION, 0.2f)),
        rebirth = 6),

    // ARSENAL FAMILIES (ids 245-256).
    ItemDraft("Wake Governor", "Turn hard and the wake surges.", UNCOMMON, listOf(DRIVE),
        clauses = listOf(on(EffectTrigger.TURN, EffectAction.RefreshWeapon(0.4f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.MOMENTUM)), cooldown = 0.75f)),
        unlock = 5),
    ItemDraft("Epicycle Gears", "Every collision spins the orbit faster.", UNCOMMON, listOf(RAM),
        clauses = listOf(on(EffectTrigger.IMPACT, EffectAction.RefreshWeapon(0.2f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.ORBITAL)), cooldown = 0.5f)),
        unlock = 5),
    ItemDraft("Halo Condenser", "A blow to the hull flares the aura around it.", UNCOMMON, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.RefreshWeapon(0.6f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.AURA)), cooldown = 1f)),
        unlock = 5),
    ItemDraft("Homing Rifling", "A clean hit bends the next shots toward it.", RARE, listOf(PRECISION), listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.CRIT, EffectAction.Zone(ZoneKind.ATTRACTOR, 200f, 3f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.PROJECTILE)), cooldown = 2f)),
        unlock = 8),
    ItemDraft("Tripwire Spool", "Stop, and the traps reset themselves.", COMMON, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.BRAKE_RELEASE, EffectAction.RefreshWeapon(0.8f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.TRAP)), cooldown = 1.5f)),
        unlock = 4),
    ItemDraft("Bridge Rectifier", "Every fifth link closes the circuit into a reaction.", RARE, listOf(CHAIN), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.ForceReaction(120f, 2), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.CHAIN)), every = 5)),
        unlock = 8),
    ItemDraft("Burn-In Coil", "Run the beam until it glows, then let it all out.", RARE, listOf(OVERCLOCK),
        clauses = listOf(on(EffectTrigger.OVERHEAT, EffectAction.RefreshWeapon(1f), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.BEAM)), cooldown = 3f)),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.WeaponFamilyIs(WeaponFamily.BEAM), BuffStat.COOLING, 0.25f)),
        unlock = 8),
    ItemDraft("Shock Absorber", "Every pulse knocks a few shots out of the air.", RARE, listOf(BULWARK),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.EraseShots(160f, 2), given = listOf(EffectCondition.WeaponFamilyIs(WeaponFamily.PULSE)), cooldown = 1f, every = 3)),
        unlock = 6),
    ItemDraft("Trigger Discipline", "Steady fire keeps every other system on time.", COMMON, listOf(ARSENAL),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.RefundSkills(0.3f, SkillPick.LONGEST), every = 10)),
        unlock = 3),
    ItemDraft("Loader Sigil", "Each cast racks the weapon.", UNCOMMON, listOf(OVERCLOCK, ARSENAL),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.RefreshWeapon(0.5f))),
        unlock = 6),
    ItemDraft("Spall Rounds", "The shot cracks the shell; the Core finishes the throw.", UNCOMMON, listOf(ARSENAL, RAM),
        clauses = listOf(on(EffectTrigger.WEAPON_HIT, EffectAction.ApplyMark(MarkPick.SPALL, 3f), every = 5)),
        unlock = 6),
    ItemDraft("Counterbattery", "Every shot you stop is a round you get back.", RARE, listOf(BULWARK, ARSENAL),
        clauses = listOf(on(EffectTrigger.SHOT_ERASED, EffectAction.RefreshWeapon(0.15f), every = 2)),
        rebirth = 5),

    // WARDENS, WAGERS AND LEGENDS (ids 257-268).
    ItemDraft("Void Sponge", "The emptiness soaks up what you erase and wrings out hard light.", COMMON, listOf(BULWARK), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.SHOT_ERASED, EffectAction.Shield(4f), every = 3)),
        unlock = 3),
    ItemDraft("Static Umbrella", "When the hull is struck, the sky above it clears.", UNCOMMON, listOf(BULWARK), listOf(StatusElement.ION),
        clauses = listOf(on(EffectTrigger.HURT, EffectAction.EraseShots(220f, 6), apply(StatusElement.ION, 2, 220f), cooldown = 4f)),
        unlock = 6),
    ItemDraft("Riot Baton", "Those who cannot move cannot dodge.", RARE, listOf(ANCHOR),
        rules = listOf(ItemRule.DamageVersus(EffectCondition.TargetControlled, 0.25f)),
        unlock = 8),
    ItemDraft("Still Frame", "A control active freezes the frame around its mark.", RARE, listOf(ANCHOR), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.SKILL_CAST, EffectAction.Zone(ZoneKind.TIME_BUBBLE, 120f, 1.5f), given = listOf(EffectCondition.SkillTagged(ANCHOR)))),
        unlock = 8),
    ItemDraft("Anvil Doctrine", "If it will not move, the blow goes in instead.", RARE, listOf(RAM, ANCHOR),
        rules = listOf(ItemRule.Convert(ConversionKind.KNOCKBACK_TO_DAMAGE, 1f)),
        rebirth = 4),
    ItemDraft("Furnace Liturgy", "Feed the furnace and your actives come faster, hotter.", EPIC, listOf(OVERCLOCK),
        rules = listOf(ItemRule.Convert(ConversionKind.SKILLS_COST_HEAT, 25f), ItemRule.SkillPower(null, 0.25f)),
        unlock = 12,
        risk = true),
    ItemDraft("Kinetic Rosary", "Every Dash a bead, every bead a prayer for your actives.", EPIC, listOf(DRIVE),
        stats = listOf(stat(ItemEffect.DASH_POWER, 0.15f)),
        rules = listOf(ItemRule.Convert(ConversionKind.DASH_HEAT_TO_SKILLS, 0.4f)),
        unlock = 12,
        risk = true),
    ItemDraft("Siphon Script", "Spend the Data on actives before it ever reaches your level.", EPIC, listOf(FORTUNE),
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 60f)),
        rules = listOf(ItemRule.Convert(ConversionKind.DATA_TO_SKILLS, 0.15f)),
        unlock = 12,
        risk = true),
    ItemDraft("Hazard Pay", "Work inside the danger zone and the pay is good.", EPIC, listOf(FORTUNE, CHAIN),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.CoreInField, BuffStat.DAMAGE, 0.35f), ItemRule.ReactionRecoil(4f, 120f)),
        rebirth = 8,
        risk = true),
    ItemDraft("Philosopher's Engine", "Every reaction turns a little lead into time.", LEGENDARY, listOf(CHAIN, OVERCLOCK),
        clauses = listOf(on(EffectTrigger.REACTION, EffectAction.RefundSkills(0.25f, SkillPick.ALL), cooldown = 0.5f)),
        rules = listOf(ItemRule.SkillCooldown(null, 0.15f), ItemRule.CatalysisRate(0.5f)),
        rebirth = 2),
    ItemDraft("Summit Crown", "Worn only at the top, and only by those who climbed.", LEGENDARY, listOf(OVERCLOCK),
        clauses = listOf(on(EffectTrigger.APEX, EffectAction.EraseShots(400f, 12), EffectAction.Shield(60f)), on(EffectTrigger.LEVEL_UP, EffectAction.ChargeApex(5f))),
        rules = listOf(ItemRule.ApexCooldown(0.2f)),
        rebirth = 10),
    ItemDraft("Meridian Engine", "Cross into a field and the engine turns over.", LEGENDARY, listOf(ANCHOR),
        clauses = listOf(on(EffectTrigger.FIELD_ENTER, EffectAction.RefundSkills(1f, SkillPick.ALL), EffectAction.Vent(20f), cooldown = 2f)),
        rules = listOf(ItemRule.FieldTuning(FieldAspect.RADIUS, 0.3f)),
        rebirth = 5),
)

/** The shipped active effects the active items are authored from; initialised before [ActiveDrafts] reads them. */
private val DefaultSkills: List<SkillDefinition> = defaultSkillDefinitions()

/**
 * ACTIVES (ids 269-286), appended in [SkillId] order so an active's id is 269 + its ordinal. Each item carries one
 * active effect and one small passive rule, thematic to its element and tags and no larger than the shipped items'
 * median of its kind allows (the content test pins the table). Uncommon, Rare and Epic only; none waits for a Rebirth.
 */
private val ActiveDrafts: List<ItemDraft> = SkillId.entries.map { id ->
    when (id) {
        SkillId.SOLAR_FLARE -> active(id, RARE, ItemRule.ElementPotency(StatusElement.THERMAL, 0.2f))
        SkillId.METEOR -> active(id, RARE, ItemRule.BreakPower(StatusElement.THERMAL, 0.25f))
        SkillId.FROST_LINE -> active(id, UNCOMMON, ItemRule.StatusDuration(StatusElement.CRYO, 1.2f))
        SkillId.FROST_SNAP -> active(id, RARE, ItemRule.CritDamageVersus(EffectCondition.TargetFrozen, 0.15f))
        SkillId.ARC_HOOK -> active(id, UNCOMMON, ItemRule.ElementPotency(StatusElement.ION, 0.2f))
        SkillId.STORM_COIL -> active(id, RARE, ItemRule.BreakPower(StatusElement.ION, 0.25f))
        SkillId.GRAVITY_WELL -> active(id, RARE, ItemRule.BreakPower(StatusElement.GRAVITIC, 0.25f))
        SkillId.MASS_DRIVER -> active(id, RARE, ItemRule.ElementPotency(StatusElement.GRAVITIC, 0.2f))
        SkillId.DECAY_SPORES -> active(id, RARE, ItemRule.ElementPotency(StatusElement.ENTROPY, 0.2f))
        SkillId.ENTROPIC_TIDE -> active(id, EPIC, ItemRule.StatusDuration(StatusElement.ENTROPY, 1.2f))
        SkillId.SONIC_BOOM -> active(id, UNCOMMON, ItemRule.BreakPower(StatusElement.SONIC, 0.25f))
        SkillId.RESONATOR -> active(id, EPIC, ItemRule.StatusDuration(StatusElement.SONIC, 1.2f))
        SkillId.VOID_STEP -> active(id, UNCOMMON, ItemRule.BreakPower(StatusElement.VOID, 0.25f))
        SkillId.NULL_SHROUD -> active(id, RARE, ItemRule.ElementPotency(StatusElement.VOID, 0.2f))
        SkillId.SLINGSHOT ->
            active(id, UNCOMMON, ItemRule.WhileModifier(EffectCondition.SpeedAtLeast(900f), BuffStat.COOLING, 0.25f))
        SkillId.FULL_STOP ->
            active(id, UNCOMMON, ItemRule.WhileModifier(EffectCondition.Braking, BuffStat.DAMAGE_TAKEN, -0.15f))
        SkillId.CATALYZE -> active(id, EPIC, ItemRule.ReactionPower(0.15f))
        SkillId.DUAL_INFUSION -> active(id, EPIC, ItemRule.DamageVersus(EffectCondition.TargetHas(null), 0.08f))
    }
}

/**
 * The active item for [id]: its name, tags and element are the effect's own and its flavor line is the effect's summary
 * (bootstrap checks the same equality for every catalog); it stacks to rank III and unlocks by run level per rarity.
 */
private fun active(id: SkillId, rarity: ItemRarity, passive: ItemRule): ItemDraft {
    val effect = DefaultSkills[id.ordinal]
    return ItemDraft(
        effect.name, effect.summary, rarity, effect.tags, listOfNotNull(effect.element),
        rules = listOf(passive),
        stacks = ContentBounds.MAX_SKILL_RANK,
        unlock = activeUnlockLevel(rarity),
        active = id,
    )
}

/** The run level an active item of [rarity] waits for (calibration moves these per rarity, never the rarities). */
private fun activeUnlockLevel(rarity: ItemRarity): Int = when (rarity) {
    UNCOMMON -> 5
    RARE -> 7
    EPIC -> 12
    COMMON, LEGENDARY -> error("Active items are Uncommon, Rare or Epic")
}

/** A reaction clause condition naming both elements: it holds only for the reaction between them. */
private fun pair(first: StatusElement, second: StatusElement): List<EffectCondition> =
    listOf(EffectCondition.ElementIs(first), EffectCondition.ElementIs(second))

internal fun defaultItems(): ImmutableList<ItemDefinition> =
    (Drafts + ActiveDrafts).mapIndexed { index, draft ->
        ItemDefinition(
            id = index,
            name = draft.name,
            description = draft.flavor,
            rarity = draft.rarity,
            tags = draft.tags.toImmutableList(),
            elements = draft.elements.toImmutableList(),
            stats = draft.stats.toImmutableList(),
            clauses = draft.clauses.toImmutableList(),
            rules = draft.rules.toImmutableList(),
            maxStacks = draft.stacks,
            unlockLevel = draft.unlock,
            minimumRebirth = draft.rebirth,
            risk = draft.risk,
            active = draft.active,
        )
    }.toImmutableList().also(::validateItems)

internal fun validateItems(items: List<ItemDefinition>) {
    check(items.size <= ContentBounds.MAX_ITEMS) { "Item catalog exceeds ${ContentBounds.MAX_ITEMS} items" }
    check(items.withIndex().all { (index, item) -> item.id == index }) { "Item ids must be contiguous from zero" }
    check(items.map { it.name }.toSet().size == items.size) { "Item names must be unique" }
    check(items.map { it.description }.toSet().size == items.size) { "Item flavor lines must be unique" }
    check(items.all { it.tags.isNotEmpty() || it.elements.isNotEmpty() || it.rules.any { rule -> rule is ItemRule.ReactionPower || rule is ItemRule.ReactionResidue } }) {
        "Every item belongs to an archetype, an element or the reaction family"
    }
    BuildTag.entries.forEach { tag ->
        check(items.count { tag in it.tags } >= 5) { "$tag needs at least five items to be draftable" }
    }
    StatusElement.entries.forEach { element ->
        check(items.count { element in it.elements } >= 5) { "$element needs at least five items" }
        check(items.any { element in it.elements && it.unlockLevel <= 2 }) { "$element needs an early enabler" }
    }
    validateExclusiveRules(items)
}

/**
 * A reaction has at most one keystone item, which declares both of the reaction's elements (the item itself is
 * Epic or Legendary by construction); at most one item grants skill charges and at most one attunes skills.
 */
internal fun validateExclusiveRules(items: List<ItemDefinition>) {
    val keystones = items.mapNotNull { item -> item.keystone?.let { it to item } }
    keystones.groupBy({ it.first }, { it.second.name }).forEach { (reaction, names) ->
        check(names.size == 1) { "$reaction has more than one keystone item: $names" }
    }
    keystones.forEach { (reaction, item) ->
        check(reaction.first in item.elements && reaction.second in item.elements) {
            "${item.name} switches $reaction to its keystone without declaring both of its elements"
        }
    }
    check(items.count { item -> item.rules.any { it is ItemRule.SkillCharges } } <= 1) { "At most one item grants skill charges" }
    check(items.count { item -> item.rules.any { it is ItemRule.SkillAttunement } } <= 1) { "At most one item attunes skills" }
}

/**
 * Progressive content validation: every explicit element an item touches is declared on the item (so
 * cards and affinity counts never lie), and no combination of threshold reductions can drop a Break
 * below two stacks.
 */
internal fun validateBuildReferences(items: List<ItemDefinition>, build: kinetickk.ball.content.api.BuildCatalog) {
    items.forEach { item ->
        val touched = buildSet {
            item.clauses.forEach { clause ->
                clause.actions.forEach { action ->
                    when (action) {
                        is EffectAction.ApplyStatus -> action.pick.element?.let(::add)
                        is EffectAction.Chain -> action.pick?.element?.let(::add)
                        is EffectAction.Shards -> action.pick?.element?.let(::add)
                        is EffectAction.Well -> action.pick?.element?.let(::add)
                        else -> Unit
                    }
                }
            }
            item.rules.forEach { rule ->
                when (rule) {
                    is ItemRule.ElementPotency -> add(rule.element)
                    is ItemRule.StatusDuration -> add(rule.element)
                    is ItemRule.BreakThreshold -> add(rule.element)
                    is ItemRule.BreakPower -> add(rule.element)
                    is ItemRule.BreakSpread -> add(rule.element)
                    is ItemRule.SkillAttunement -> rule.pick.element?.let(::add)
                    is ItemRule.SkillCooldown -> rule.element?.let(::add)
                    is ItemRule.SkillPower -> rule.element?.let(::add)
                    else -> Unit
                }
            }
        }
        require(item.elements.containsAll(touched)) { "${item.name} touches undeclared elements $touched" }
    }
    StatusElement.entries.forEach { element ->
        val reduction = items.sumOf { item ->
            item.rules.filterIsInstance<ItemRule.BreakThreshold>().filter { it.element == element }.sumOf { it.reduction }
        }
        // One affinity tier also lowers the threshold by one.
        require(build.element(element).breakThreshold - reduction - 1 >= 2) {
            "$element break threshold can fall below two stacks"
        }
    }
}
