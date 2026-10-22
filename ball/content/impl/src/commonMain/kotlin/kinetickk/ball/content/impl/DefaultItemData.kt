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
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.LEGENDARY
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.StatDelta
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
        stats = listOf(stat(ItemEffect.PICKUP_RADIUS, 90f), stat(ItemEffect.DATA_GAIN, 0.05f))),
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
            EffectAction.Burst(150f, DamageBasis.VICTIM_MAX_HP, 0.4f), EffectAction.GainData(1f),
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
)

/** A reaction clause condition naming both elements: it holds only for the reaction between them. */
private fun pair(first: StatusElement, second: StatusElement): List<EffectCondition> =
    listOf(EffectCondition.ElementIs(first), EffectCondition.ElementIs(second))

internal fun defaultItems(): ImmutableList<ItemDefinition> =
    Drafts.mapIndexed { index, draft ->
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
