// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

internal fun defaultWeapons(): ImmutableList<WeaponDefinition> =
    listOf(
        WeaponDefinition(WeaponId.FLUX_WAKE, "Flux Wake", "High-speed movement leaves a damaging trail that lingers behind the Core.", listOf("MOMENTUM", "TRAIL", "AREA"), 0),
        WeaponDefinition(WeaponId.MORNINGSTAR, "Morningstar", "A heavy orbital mass converts velocity and mass into crushing contact damage.", listOf("ORBITAL", "IMPACT", "MASS"), 25),
        WeaponDefinition(WeaponId.PHASE_LATTICE, "Phase Lattice", "A pulsing gravity ring damages enemies held near its unstable perimeter.", listOf("AURA", "CONTROL", "PHASE"), 55),
        WeaponDefinition(WeaponId.NULL_LANCE, "Null Lance", "Momentum periodically projects a piercing lance along the Core's travel vector.", listOf("PIERCE", "DIRECTIONAL", "MOMENTUM"), 95),
        WeaponDefinition(WeaponId.GRAVITY_MINES, "Gravity Mines", "Braking plants implosive mines that pull enemies inward before detonating.", listOf("MINE", "PULL", "BRAKE"), 145),
        WeaponDefinition(WeaponId.ION_SWARM, "Ion Swarm", "Autonomous ion motes orbit the Core, seek targets, and accelerate with attack speed.", listOf("DRONE", "SEEKING", "RAPID"), 215),
        WeaponDefinition(WeaponId.RIFT_BLADES, "Rift Blades", "Paired blades tear outward through enemies and return across their original paths.", listOf("BLADE", "RETURNING", "CRITICAL"), 305),
        WeaponDefinition(WeaponId.ARC_COIL, "Arc Coil", "Stored kinetic charge erupts as lightning that chains between nearby enemies.", listOf("CHAIN", "LIGHTNING", "CHARGE"), 430),
        WeaponDefinition(WeaponId.QUASAR_CANNON, "Quasar Cannon", "A slow-charging cannon compresses momentum into a colossal piercing projectile.", listOf("HEAVY", "CHARGE", "PIERCE"), 610),
        WeaponDefinition(WeaponId.ENTROPY_FIELD, "Entropy Field", "A widening decay field slows hostile motion and deals escalating damage over time.", listOf("AURA", "DECAY", "SLOW"), 860),
        WeaponDefinition(WeaponId.SINGULARITY_SPEAR, "Singularity Spear", "Every overdrive cycle forges a boss-piercing spear from condensed kinetic matter.", listOf("ULTIMATE", "OVERDRIVE", "BOSS"), 1_200),
        WeaponDefinition(WeaponId.PRISM_RELAY, "Prism Relay", "Launches a seeking light shard that refracts between targets; mastery adds ricochets and twin relays.", listOf("SEEKING", "RICOCHET", "REFRACTION"), 1_650),
    ).toImmutableList().also(::validateWeapons)

private fun validateWeapons(weapons: List<WeaponDefinition>) {
    check(WeaponId.entries.size == 12) { "WeaponId must contain exactly 12 weapons" }
    check(weapons.size == 12) { "Weapon catalog must contain exactly 12 weapons" }
    check(weapons.withIndex().all { (index, weapon) -> weapon.id.ordinal == index }) {
        "Weapon catalog order must match WeaponId order"
    }
    check(weapons.map { it.id }.toSet().size == weapons.size) { "Weapon ids must be unique" }
    check(weapons.map { it.name }.toSet().size == weapons.size) { "Weapon names must be unique" }
}
