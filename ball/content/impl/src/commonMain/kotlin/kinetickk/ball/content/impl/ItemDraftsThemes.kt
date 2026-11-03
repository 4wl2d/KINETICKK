// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.ARSENAL
import kinetickk.ball.content.api.BuildTag.BULWARK
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.FORTUNE
import kinetickk.ball.content.api.BuildTag.OVERCLOCK
import kinetickk.ball.content.api.BuildTag.PRECISION
import kinetickk.ball.content.api.BuildTag.RAM
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.LEGENDARY
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.foundation.collections.toImmutableList

internal val ThemeDrafts: List<ItemDraft> = listOf(
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
