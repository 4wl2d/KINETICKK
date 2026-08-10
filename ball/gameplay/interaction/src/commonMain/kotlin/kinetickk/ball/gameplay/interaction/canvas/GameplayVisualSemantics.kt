// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.DamageNumberTier
import kinetickk.foundation.design.Acid
import kinetickk.foundation.design.Blue
import kinetickk.foundation.design.Cyan
import kinetickk.foundation.design.Gold
import kinetickk.foundation.design.Magenta
import kinetickk.foundation.design.Muted
import kinetickk.foundation.design.Orange
import kinetickk.foundation.design.Red
import kinetickk.foundation.design.SystemGlyphStyle
import kinetickk.foundation.design.Violet
import kinetickk.foundation.design.White

internal val ParticleColors = listOf(Cyan, Violet, Magenta, Acid, Red)
private val DamagePale = Color(0xFFFFF2C2)

internal fun damageNumberColor(tier: DamageNumberTier): Color = when (tier) {
    DamageNumberTier.STANDARD -> DamagePale
    DamageNumberTier.STRONG -> Gold
    DamageNumberTier.POWERFUL -> Orange
    DamageNumberTier.DEVASTATING -> Red
}

internal fun damageNumberScale(tier: DamageNumberTier): Float = when (tier) {
    DamageNumberTier.STANDARD -> 0.95f
    DamageNumberTier.STRONG -> 1.03f
    DamageNumberTier.POWERFUL -> 1.12f
    DamageNumberTier.DEVASTATING -> 1.25f
}

internal fun rarityColor(rarity: ItemRarity): Color = when (rarity) {
    ItemRarity.COMMON -> Muted
    ItemRarity.UNCOMMON -> Cyan
    ItemRarity.RARE -> Violet
    ItemRarity.EPIC -> Magenta
    ItemRarity.LEGENDARY -> Acid
}

internal fun weaponColor(id: WeaponId): Color = when (id) {
    WeaponId.FLUX_WAKE -> Cyan
    WeaponId.MORNINGSTAR -> Violet
    WeaponId.PHASE_LATTICE -> Magenta
    WeaponId.NULL_LANCE -> Acid
    WeaponId.GRAVITY_MINES -> Orange
    WeaponId.ION_SWARM -> Cyan
    WeaponId.RIFT_BLADES -> Magenta
    WeaponId.ARC_COIL -> Violet
    WeaponId.QUASAR_CANNON -> Orange
    WeaponId.ENTROPY_FIELD -> Red
    WeaponId.SINGULARITY_SPEAR -> White
    WeaponId.PRISM_RELAY -> Blue
}

internal fun weaponGlyphStyle(id: WeaponId): SystemGlyphStyle = when (id) {
    WeaponId.FLUX_WAKE -> SystemGlyphStyle.DIAGONAL_SLASH
    WeaponId.MORNINGSTAR -> SystemGlyphStyle.ORBITING_NODE
    WeaponId.PHASE_LATTICE -> SystemGlyphStyle.CONCENTRIC_RING
    WeaponId.NULL_LANCE -> SystemGlyphStyle.ARROW_LINE
    WeaponId.GRAVITY_MINES -> SystemGlyphStyle.HEX_ORBIT
    WeaponId.ION_SWARM -> SystemGlyphStyle.DIAMOND_TRIAD
    WeaponId.RIFT_BLADES -> SystemGlyphStyle.TWIN_DIAMONDS
    WeaponId.ARC_COIL -> SystemGlyphStyle.ZIGZAG_RING
    WeaponId.QUASAR_CANNON -> SystemGlyphStyle.RINGED_BEAM
    WeaponId.ENTROPY_FIELD -> SystemGlyphStyle.HEPTAGON_ORBIT
    WeaponId.SINGULARITY_SPEAR -> SystemGlyphStyle.SPEAR_LINE
    WeaponId.PRISM_RELAY -> SystemGlyphStyle.TRIANGLE_NETWORK
}
