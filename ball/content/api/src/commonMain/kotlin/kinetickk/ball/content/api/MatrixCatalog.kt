// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.ball.content.api.MatrixBranch.BULWARK
import kinetickk.ball.content.api.MatrixBranch.FORTUNE
import kinetickk.ball.content.api.MatrixBranch.KINETIC
import kinetickk.ball.content.api.MatrixBranch.SYSTEMS
import kinetickk.ball.content.api.MatrixNodeId.AEGIS_LATTICE
import kinetickk.ball.content.api.MatrixNodeId.ARMED_START
import kinetickk.ball.content.api.MatrixNodeId.BASTION_PROTOCOL
import kinetickk.ball.content.api.MatrixNodeId.CATALYST_CORE
import kinetickk.ball.content.api.MatrixNodeId.CRITICAL_LATTICE
import kinetickk.ball.content.api.MatrixNodeId.CRYO_LOOP
import kinetickk.ball.content.api.MatrixNodeId.DATA_ARCHIVE
import kinetickk.ball.content.api.MatrixNodeId.ELITE_BOUNTY
import kinetickk.ball.content.api.MatrixNodeId.GLASS_ENGINE
import kinetickk.ball.content.api.MatrixNodeId.GRAVITY_SURFER
import kinetickk.ball.content.api.MatrixNodeId.HARDLIGHT_RESERVE
import kinetickk.ball.content.api.MatrixNodeId.HEIRLOOM_VAULT
import kinetickk.ball.content.api.MatrixNodeId.IMPACT_DAMPERS
import kinetickk.ball.content.api.MatrixNodeId.IMPACT_DRIVER
import kinetickk.ball.content.api.MatrixNodeId.JUGGERNAUT
import kinetickk.ball.content.api.MatrixNodeId.LUCKY_CHARM
import kinetickk.ball.content.api.MatrixNodeId.MAGNET_ARRAY
import kinetickk.ball.content.api.MatrixNodeId.NANOFORGE
import kinetickk.ball.content.api.MatrixNodeId.NEAR_MISS
import kinetickk.ball.content.api.MatrixNodeId.OVERCLOCKED_REFLEXES
import kinetickk.ball.content.api.MatrixNodeId.OVERDRIVE_COIL
import kinetickk.ball.content.api.MatrixNodeId.PERPETUAL_DRIFT
import kinetickk.ball.content.api.MatrixNodeId.PROSPECTOR
import kinetickk.ball.content.api.MatrixNodeId.REBORN_ARSENAL
import kinetickk.ball.content.api.MatrixNodeId.REINFORCED_HULL
import kinetickk.ball.content.api.MatrixNodeId.REROLL_CACHE
import kinetickk.ball.content.api.MatrixNodeId.RICOCHET_RIG
import kinetickk.ball.content.api.MatrixNodeId.SALVAGE_PROTOCOL
import kinetickk.ball.content.api.MatrixNodeId.SECOND_WIND
import kinetickk.ball.content.api.MatrixNodeId.VECTOR_THRUSTERS
import kinetickk.ball.content.api.MatrixNodeId.WEAPON_CALIBRATION
import kinetickk.ball.content.api.MatrixNodeId.WIDE_OFFER
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

/**
 * The shipped Core Matrix in [MatrixNodeId] order: four branches of eight nodes. Every first rank
 * of a stat node gives at least +15 % (or a visible behavior); each branch ends in a keystone.
 */
fun defaultMatrixNodes(): ImmutableList<MatrixNodeDefinition> = DefaultMatrixNodes

private val DefaultMatrixNodes: ImmutableList<MatrixNodeDefinition> = listOf(
    // Kinetic: impact, weapons, criticals and Overdrive.
    node(IMPACT_DRIVER, "Impact Driver", "+15% impact damage per rank.", KINETIC, 0, listOf(150, 400, 900),
        effects = listOf(stat(ItemEffect.IMPACT_DAMAGE, 0.15f))),
    node(WEAPON_CALIBRATION, "Weapon Calibration", "+12% weapon power per rank.", KINETIC, 0, listOf(200, 500, 1_000),
        effects = listOf(stat(ItemEffect.WEAPON_POWER, 0.12f))),
    node(CRITICAL_LATTICE, "Critical Lattice", "+6% critical chance and +25% critical damage per rank.", KINETIC, 1,
        listOf(600, 1_400), requires = listOf(IMPACT_DRIVER),
        effects = listOf(stat(ItemEffect.CRIT_CHANCE, 0.06f), stat(ItemEffect.CRIT_DAMAGE, 0.25f))),
    node(ARMED_START, "Armed Start", "Each rank starts runs one weapon level higher and raises the automatic weapon level cap by one.",
        KINETIC, 1, listOf(800, 2_000), requires = listOf(WEAPON_CALIBRATION),
        effects = listOf(MatrixEffect.StartingWeaponLevel(1))),
    node(CATALYST_CORE, "Catalyst Core", "Reactions deal 20% more damage and statuses are 15% more potent.", KINETIC, 2,
        listOf(1_600), requires = listOf(CRITICAL_LATTICE, ARMED_START),
        effects = listOf(MatrixEffect.ReactionPower(0.2f), MatrixEffect.StatusPotency(0.15f))),
    node(OVERDRIVE_COIL, "Overdrive Coil", "Start every run with 50 Overdrive and gain Overdrive 25% faster.", KINETIC, 2,
        listOf(1_800), requires = listOf(ARMED_START), rebirth = 1,
        effects = listOf(MatrixEffect.StartingOverdrive(50f), stat(ItemEffect.OVERDRIVE_GAIN, 0.25f))),
    node(REBORN_ARSENAL, "Reborn Arsenal", "Weapon power gains up to an extra +40% as the run progresses.", KINETIC, 3,
        listOf(4_200), requires = listOf(OVERDRIVE_COIL), rebirth = 6,
        effects = listOf(MatrixEffect.WeaponRunGrowth(0.4f))),
    node(GLASS_ENGINE, "Glass Engine", "All damage +35%. Maximum integrity −30.", KINETIC, 3,
        listOf(3_500), requires = listOf(CATALYST_CORE), rebirth = 3, keystone = true,
        effects = listOf(MatrixEffect.DamageMultiplier(0.35f), stat(ItemEffect.MAX_INTEGRITY, -30f))),

    // Bulwark: integrity, shields and survival.
    node(REINFORCED_HULL, "Reinforced Hull", "+25 maximum integrity per rank.", BULWARK, 0, listOf(120, 350, 800),
        effects = listOf(stat(ItemEffect.MAX_INTEGRITY, 25f))),
    node(HARDLIGHT_RESERVE, "Hardlight Reserve", "+20 shield capacity per rank.", BULWARK, 0, listOf(300, 800),
        effects = listOf(stat(ItemEffect.SHIELD_CAPACITY, 20f))),
    node(IMPACT_DAMPERS, "Impact Dampers",
        "Obstacle collisions deal 25% less damage per rank, and the lethal collision speed rises by 150 per rank.", BULWARK, 1,
        listOf(400, 1_000), requires = listOf(REINFORCED_HULL),
        effects = listOf(MatrixEffect.ObstacleGuard(damageMultiplier = 0.75f, lethalSpeedBonus = 150f))),
    node(SECOND_WIND, "Second Wind", "Once per run, lethal damage leaves you at 30% integrity with 2 seconds of invulnerability.",
        BULWARK, 2, listOf(2_200), requires = listOf(HARDLIGHT_RESERVE, IMPACT_DAMPERS), rebirth = 1,
        effects = listOf(MatrixEffect.SecondWind(integrityFraction = 0.3f, invulnerableSeconds = 2f))),
    node(NANOFORGE, "Nanoforge", "+0.8 integrity regeneration per second per rank.", BULWARK, 1, listOf(700, 1_600),
        requires = listOf(REINFORCED_HULL), effects = listOf(stat(ItemEffect.REGEN, 0.8f))),
    node(BASTION_PROTOCOL, "Bastion Protocol", "+12% damage reduction.", BULWARK, 2, listOf(2_600),
        requires = listOf(NANOFORGE), rebirth = 2, effects = listOf(stat(ItemEffect.DAMAGE_REDUCTION, 0.12f))),
    node(AEGIS_LATTICE, "Aegis Lattice", "+30 shield capacity, and the shield starts recharging 40% sooner.", BULWARK, 3,
        listOf(4_500), requires = listOf(BASTION_PROTOCOL), rebirth = 7,
        effects = listOf(stat(ItemEffect.SHIELD_CAPACITY, 30f), MatrixEffect.ShieldRegenDelay(0.6f))),
    node(JUGGERNAUT, "Juggernaut", "Mass +30%, impact damage +20%, contact damage taken −30%, drag +10%.", BULWARK, 3,
        listOf(3_800), requires = listOf(BASTION_PROTOCOL), rebirth = 4, keystone = true,
        effects = listOf(
            stat(ItemEffect.MASS, 0.3f), stat(ItemEffect.IMPACT_DAMAGE, 0.2f),
            MatrixEffect.ContactDamage(0.7f), MatrixEffect.Drag(1.1f),
        )),

    // Systems: pickups, heat, Dash and obstacles.
    node(MAGNET_ARRAY, "Magnet Array", "+70 pickup radius and +6% Data from each pickup per rank.", SYSTEMS, 0,
        listOf(100, 300, 700), effects = listOf(stat(ItemEffect.PICKUP_RADIUS, 70f), MatrixEffect.PickupValue(0.06f))),
    node(CRYO_LOOP, "Cryo Loop", "+20% cooling and 10% less Dash heat per rank.", SYSTEMS, 0, listOf(250, 700),
        effects = listOf(stat(ItemEffect.COOLING, 0.2f), MatrixEffect.DashHeat(0.9f))),
    node(VECTOR_THRUSTERS, "Vector Thrusters", "+15% Dash impulse per rank.", SYSTEMS, 1, listOf(400, 1_000),
        requires = listOf(CRYO_LOOP), effects = listOf(stat(ItemEffect.DASH_POWER, 0.15f))),
    node(NEAR_MISS, "Near Miss", "Passing within 40 of an asteroid at 900 speed or more grants 6 Overdrive, at most every 0.5 seconds.",
        SYSTEMS, 1, listOf(900), requires = listOf(MAGNET_ARRAY),
        effects = listOf(MatrixEffect.NearMiss(distance = 40f, minSpeed = 900f, overdrive = 6f, cooldown = 0.5f))),
    node(RICOCHET_RIG, "Ricochet Rig",
        "Asteroid bounces keep 75% of your speed instead of 60%, and each ricochet bursts for impact damage within 140.", SYSTEMS, 2,
        listOf(1_500), requires = listOf(VECTOR_THRUSTERS), rebirth = 1,
        effects = listOf(MatrixEffect.RicochetRig(restitution = 0.75f, burstRadius = 140f))),
    node(OVERCLOCKED_REFLEXES, "Overclocked Reflexes", "+12% attack speed per rank.", SYSTEMS, 2, listOf(1_200, 2_600),
        requires = listOf(VECTOR_THRUSTERS), effects = listOf(stat(ItemEffect.ATTACK_SPEED, 0.12f))),
    node(GRAVITY_SURFER, "Gravity Surfer", "Ricochets grant 0.4 seconds of Dash phase, and the lethal collision speed rises by 250.",
        SYSTEMS, 3, listOf(5_000), requires = listOf(RICOCHET_RIG), rebirth = 9,
        effects = listOf(MatrixEffect.RicochetPhase(0.4f), MatrixEffect.ObstacleGuard(damageMultiplier = 1f, lethalSpeedBonus = 250f))),
    node(PERPETUAL_DRIFT, "Perpetual Drift", "Drag −40% and magnetism −10%. One Dash every 6 seconds costs no heat.", SYSTEMS, 3,
        listOf(4_000), requires = listOf(OVERCLOCKED_REFLEXES), rebirth = 5, keystone = true,
        effects = listOf(MatrixEffect.Drag(0.6f), stat(ItemEffect.MAGNETISM, -0.1f), MatrixEffect.FreeDashInterval(6f))),

    // Fortune: Matter, Data and offers.
    node(SALVAGE_PROTOCOL, "Salvage Protocol", "+15% Matter per rank.", FORTUNE, 0, listOf(150, 400, 900),
        effects = listOf(stat(ItemEffect.MATTER_GAIN, 0.15f))),
    node(DATA_ARCHIVE, "Data Archive", "+15% Data per rank.", FORTUNE, 0, listOf(150, 400, 900),
        effects = listOf(stat(ItemEffect.DATA_GAIN, 0.15f))),
    node(REROLL_CACHE, "Reroll Cache", "+1 reroll per rank at the start of every run.", FORTUNE, 1, listOf(500, 1_500),
        requires = listOf(DATA_ARCHIVE), effects = listOf(MatrixEffect.Rerolls(1))),
    node(WIDE_OFFER, "Wide Offer", "Every fourth item offer shows four cards.", FORTUNE, 2, listOf(1_200),
        requires = listOf(REROLL_CACHE), effects = listOf(MatrixEffect.OfferCards(4))),
    node(LUCKY_CHARM, "Lucky Charm", "+0.5 luck per rank. A Rare or better card is guaranteed after 4 offers instead of 6.",
        FORTUNE, 1, listOf(800, 2_000), requires = listOf(SALVAGE_PROTOCOL),
        effects = listOf(stat(ItemEffect.LUCK, 0.5f), MatrixEffect.RarityPity(4))),
    node(ELITE_BOUNTY, "Elite Bounty", "Each Elite kill grants an extra item offer.", FORTUNE, 2, listOf(2_400),
        requires = listOf(LUCKY_CHARM), rebirth = 2, effects = listOf(MatrixEffect.EliteBounty)),
    node(HEIRLOOM_VAULT, "Heirloom Vault",
        "+1 Heirloom offer. When no card can be added, the Heirloom you take starts at rank 2. +5% Reborn card chance.",
        FORTUNE, 3, listOf(3_000), requires = listOf(ELITE_BOUNTY), rebirth = 3,
        effects = listOf(MatrixEffect.HeirloomOffers(1), MatrixEffect.RebornChance(0.05f))),
    node(PROSPECTOR, "Prospector", "Matter +50%. Enemies have 12% more integrity.", FORTUNE, 3, listOf(3_200),
        requires = listOf(WIDE_OFFER), rebirth = 4, keystone = true,
        effects = listOf(stat(ItemEffect.MATTER_GAIN, 0.5f), MatrixEffect.EnemyIntegrity(1.12f))),
).toImmutableList()

private fun stat(effect: ItemEffect, amount: Float): MatrixEffect = MatrixEffect.Stat(StatDelta(effect, amount))

private fun node(
    id: MatrixNodeId,
    name: String,
    description: String,
    branch: MatrixBranch,
    row: Int,
    costs: List<Int>,
    requires: List<MatrixNodeId> = emptyList(),
    rebirth: Int = 0,
    keystone: Boolean = false,
    effects: List<MatrixEffect>,
): MatrixNodeDefinition = MatrixNodeDefinition(
    id = id,
    name = name,
    description = description,
    branch = branch,
    row = row,
    maxRanks = costs.size,
    costs = costs.toImmutableList(),
    prerequisites = requires.toImmutableList(),
    minimumRebirth = rebirth,
    keystone = keystone,
    effects = effects.toImmutableList(),
)
