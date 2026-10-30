// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag.ANCHOR
import kinetickk.ball.content.api.BuildTag.BULWARK
import kinetickk.ball.content.api.BuildTag.CHAIN
import kinetickk.ball.content.api.BuildTag.DRIVE
import kinetickk.ball.content.api.BuildTag.FORTUNE
import kinetickk.ball.content.api.BuildTag.OVERCLOCK
import kinetickk.ball.content.api.BuildTag.PRECISION
import kinetickk.ball.content.api.BuildTag.RAM
import kinetickk.ball.content.api.DamageBasis
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ElementPick
import kinetickk.ball.content.api.ItemRarity.COMMON
import kinetickk.ball.content.api.ItemRarity.EPIC
import kinetickk.ball.content.api.ItemRarity.RARE
import kinetickk.ball.content.api.ItemRarity.UNCOMMON
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.ObstacleAspect
import kinetickk.ball.content.api.ObstacleKind
import kinetickk.ball.content.api.StatusElement
import kinetickk.foundation.collections.toImmutableList

// The same compact authoring vocabulary as the default items; every value is validated by the api constructors.
private fun on(
    trigger: EffectTrigger,
    vararg actions: EffectAction,
    given: List<EffectCondition> = emptyList(),
    cooldown: Float = 0f,
) = EffectClause(trigger, given.toImmutableList(), actions.toList().toImmutableList(), cooldown)

private fun apply(element: StatusElement, stacks: Int, radius: Float) =
    EffectAction.ApplyStatus(ElementPick.of(element), stacks, radius)

/** The trigger is about an obstacle of [kind]. */
private fun about(kind: ObstacleKind): List<EffectCondition> = listOf(EffectCondition.ObstacleIs(kind))

/**
 * Items built around the obstacle kinds, appended after the active items (ids 287..301): two for each kind the 0.6.0
 * field adds, one for bumpers, one for asteroids and one guard against collisions, on existing tags only. Glass Harvest
 * waits for Reborn 1 so the rank-0 Data ladder (the deck's Data floor plus the Data Archive's stratum) stays its 13 items.
 */
internal val ObstacleDrafts: List<ItemDraft> = listOf(
    // Crystals: what a broken one leaves behind.
    ItemDraft("Glass Harvest", "Every crystal you break was full of Data.", COMMON, listOf(FORTUNE, RAM),
        clauses = listOf(on(EffectTrigger.SHATTER, EffectAction.GainData(3f), given = about(ObstacleKind.CRYSTAL))), unlock = 3,
        rebirth = 1),
    ItemDraft("Rime Splinters", "A crystal breaks into a hundred cold knives.", UNCOMMON, listOf(CHAIN), listOf(StatusElement.CRYO),
        clauses = listOf(on(EffectTrigger.SHATTER, EffectAction.Shards(4, DamageBasis.FLAT, 16f, ElementPick.CRYO),
            given = about(ObstacleKind.CRYSTAL), cooldown = 0.5f)), unlock = 5),

    // Accelerator gates: the speed a crossing gives.
    ItemDraft("Accelerant Loop", "Every gate you shoot through stokes the Overdrive.", UNCOMMON, listOf(DRIVE, OVERCLOCK),
        clauses = listOf(on(EffectTrigger.BOOST, EffectAction.Overdrive(7f), given = about(ObstacleKind.ACCEL_GATE), cooldown = 0.5f)),
        unlock = 4),
    ItemDraft("Clean Line", "Thread the gate cleanly and the next shot finds the seam.", RARE, listOf(DRIVE, PRECISION),
        clauses = listOf(on(EffectTrigger.BOOST, EffectAction.RefreshWeapon(0.3f), EffectAction.Buff(BuffStat.CRIT_CHANCE, 0.35f, 1.5f),
            given = about(ObstacleKind.ACCEL_GATE), cooldown = 0.5f)),
        rules = listOf(ItemRule.ObstacleTuning(ObstacleAspect.BOOST_POWER, 0.3f)), unlock = 9),

    // Gravity knots: slinging round one and what it drags in.
    ItemDraft("Sling Harness", "Swing around a knot and it drags the swarm in after you.", RARE, listOf(ANCHOR, DRIVE),
        listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.BOOST, apply(StatusElement.GRAVITIC, 3, 240f), given = about(ObstacleKind.GRAVITY_WELL),
            cooldown = 0.5f)),
        rules = listOf(ItemRule.ObstacleTuning(ObstacleAspect.WELL_PULL, 0.4f)), unlock = 8),
    ItemDraft("Horizon Surfer", "Leave a knot at speed and a piece of it follows you out.", EPIC, listOf(ANCHOR),
        listOf(StatusElement.GRAVITIC),
        clauses = listOf(on(EffectTrigger.BOOST, EffectAction.Well(220f, 2.5f, DamageBasis.FLAT, 22f, ElementPick.GRAVITIC),
            given = about(ObstacleKind.GRAVITY_WELL), cooldown = 1.5f)),
        rules = listOf(
            ItemRule.ObstacleTuning(ObstacleAspect.WELL_PULL, 0.3f),
            ItemRule.WhileModifier(EffectCondition.NearObstacle(200f), BuffStat.DAMAGE, 0.15f),
        ), unlock = 14),

    // Derelict mines: what a blast sets off.
    ItemDraft("Sapper's Instinct", "You know which wire matters. The mines never did.", UNCOMMON, listOf(BULWARK, CHAIN),
        clauses = listOf(on(EffectTrigger.SHATTER, EffectAction.Burst(220f, DamageBasis.FLAT, 30f), given = about(ObstacleKind.DERELICT_MINE),
            cooldown = 0.5f)),
        rules = listOf(ItemRule.ObstacleTuning(ObstacleAspect.COLLISION_DAMAGE, 0.3f)), unlock = 6),
    ItemDraft("Fuse Reader", "Every blast rings along the wreckage.", RARE, listOf(CHAIN), listOf(StatusElement.SONIC),
        clauses = listOf(on(EffectTrigger.SHATTER, EffectAction.Chain(3, 300f, DamageBasis.FLAT, 24f, ElementPick.SONIC, 2),
            given = about(ObstacleKind.DERELICT_MINE), cooldown = 0.5f)), unlock = 10),

    // Phase shards: skimming one and striking one while it is real.
    ItemDraft("Phase Slip", "Skim a flickering shard and the dark leaks out after you.", UNCOMMON, listOf(DRIVE), listOf(StatusElement.VOID),
        clauses = listOf(on(EffectTrigger.GRAZE, apply(StatusElement.VOID, 3, 220f), given = about(ObstacleKind.PHASE_SHARD), cooldown = 0.5f)),
        unlock = 6),
    ItemDraft("Out of Phase", "Strike a shard while it is real and you slip half out of the world.", RARE, listOf(BULWARK, DRIVE),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.PrimeDash, EffectAction.Buff(BuffStat.DAMAGE_REDUCTION, 0.3f, 2f),
            given = about(ObstacleKind.PHASE_SHARD), cooldown = 1f)), unlock = 9),

    // Thermal vents: riding a plume.
    ItemDraft("Vent Diver", "Dive through the plume, keep the push, leave the heat.", UNCOMMON, listOf(OVERCLOCK), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.BOOST, apply(StatusElement.THERMAL, 3, 220f), EffectAction.Vent(25f),
            given = about(ObstacleKind.THERMAL_VENT), cooldown = 0.5f)), unlock = 5),
    ItemDraft("Updraft Kiln", "A vent's breath turns the hull into a hammer.", EPIC, listOf(OVERCLOCK, RAM), listOf(StatusElement.THERMAL),
        clauses = listOf(on(EffectTrigger.BOOST, EffectAction.Buff(BuffStat.IMPACT, 0.6f, 3f), given = about(ObstacleKind.THERMAL_VENT))),
        rules = listOf(
            ItemRule.ObstacleTuning(ObstacleAspect.BOOST_POWER, 0.4f),
            ItemRule.WhileModifier(EffectCondition.HeatAtLeast(60f), BuffStat.IMPACT, 0.25f),
        ), unlock = 15),

    // Bumpers, asteroids and the guard every collision build wants.
    ItemDraft("Pinball Logic", "A bumper is just a launcher you have not used yet.", COMMON, listOf(RAM, DRIVE),
        clauses = listOf(on(EffectTrigger.RICOCHET, EffectAction.Chain(3, 240f, DamageBasis.IMPACT, 0.6f), given = about(ObstacleKind.BUMPER),
            cooldown = 0.5f)), unlock = 2),
    ItemDraft("Rockhound", "Fights go better with a rock at your shoulder.", UNCOMMON, listOf(ANCHOR, RAM),
        clauses = listOf(on(EffectTrigger.GRAZE, EffectAction.Buff(BuffStat.IMPACT, 0.3f, 2.5f, maxStacks = 2), given = about(ObstacleKind.ASTEROID))),
        rules = listOf(ItemRule.WhileModifier(EffectCondition.NearObstacle(180f), BuffStat.DAMAGE, 0.12f)), unlock = 3),
    ItemDraft("Crash Cage", "A roll cage for a ball of light.", RARE, listOf(BULWARK, RAM),
        rules = listOf(
            ItemRule.ObstacleTuning(ObstacleAspect.LETHAL_SPEED, 150f),
            ItemRule.ObstacleTuning(ObstacleAspect.COLLISION_DAMAGE, 0.35f),
            ItemRule.ObstacleTuning(ObstacleAspect.RESTITUTION, 0.15f),
        ), unlock = 7),
)
