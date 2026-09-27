// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.DamageNumberTier
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.SystemGlyphStyle

/**
 * Accent list kept for reward presentation, which indexes it by position (identity accents from
 * the aspect tokens, not role colors). World rendering maps the nucleus color indices by meaning
 * through [fxColor] instead.
 */
internal val ParticleColors = listOf(Kk.AIon, Kk.AGravitic, Kk.ARift, Kk.RUncommon, Kk.AEntropy)

/**
 * Presentation meaning of a nucleus visual color index: 0 dash/Core, 1 kill or pickup, 2 weapon
 * and relic procs, 3 ram impact, elite kill and key pickups, 4 damage taken.
 */
internal fun fxColor(colorIndex: Int, roles: KkRolePalette): Color = when (colorIndex) {
    0, 3 -> roles.you
    4 -> roles.threat
    2 -> Kk.Bone2
    else -> Kk.Bone
}

/** Damage number color tiers: bone, you, heat, threat (the threshold setting picks the tier). */
internal fun damageNumberColor(tier: DamageNumberTier, roles: KkRolePalette): Color = when (tier) {
    DamageNumberTier.STANDARD -> Kk.Bone
    DamageNumberTier.STRONG -> roles.you
    DamageNumberTier.POWERFUL -> roles.heat
    DamageNumberTier.DEVASTATING -> roles.threat
}

/** Size of each tier relative to the standard number (22 / 30 / 36 / 44 px at the reference). */
internal fun damageNumberScale(tier: DamageNumberTier): Float = when (tier) {
    DamageNumberTier.STANDARD -> 1f
    DamageNumberTier.STRONG -> 1.36f
    DamageNumberTier.POWERFUL -> 1.64f
    DamageNumberTier.DEVASTATING -> 2f
}

internal fun rarityColor(rarity: ItemRarity): Color = Kk.rarity(rarity.rank)

/** One identity color per weapon for reward and build UI (aspect/rarity tokens, not role colors). */
internal fun weaponColor(id: WeaponId): Color = when (id) {
    WeaponId.FLUX_WAKE -> Kk.AIon
    WeaponId.MORNINGSTAR -> Kk.AGravitic
    WeaponId.PHASE_LATTICE -> Kk.ARift
    WeaponId.NULL_LANCE -> Kk.RUncommon
    WeaponId.GRAVITY_MINES -> Kk.RLegend
    WeaponId.ION_SWARM -> Kk.AIon
    WeaponId.RIFT_BLADES -> Kk.ARift
    WeaponId.ARC_COIL -> Kk.AGravitic
    WeaponId.QUASAR_CANNON -> Kk.RLegend
    WeaponId.ENTROPY_FIELD -> Kk.AEntropy
    WeaponId.SINGULARITY_SPEAR -> Kk.Bone
    WeaponId.PRISM_RELAY -> Kk.APrism
}

/** The redesign icon of a weapon (`icons.json weapons.*`), for HUD slots and reward cards. */
internal fun weaponIcon(id: WeaponId): KkIcon = when (id) {
    WeaponId.FLUX_WAKE -> KkIcon.WEAPONS_FLUX_WAKE
    WeaponId.MORNINGSTAR -> KkIcon.WEAPONS_MORNINGSTAR
    WeaponId.PHASE_LATTICE -> KkIcon.WEAPONS_PHASE_LATTICE
    WeaponId.NULL_LANCE -> KkIcon.WEAPONS_NULL_LANCE
    WeaponId.GRAVITY_MINES -> KkIcon.WEAPONS_GRAVITY_MINES
    WeaponId.ION_SWARM -> KkIcon.WEAPONS_ION_SWARM
    WeaponId.RIFT_BLADES -> KkIcon.WEAPONS_RIFT_BLADES
    WeaponId.ARC_COIL -> KkIcon.WEAPONS_ARC_COIL
    WeaponId.QUASAR_CANNON -> KkIcon.WEAPONS_QUASAR_CANNON
    WeaponId.ENTROPY_FIELD -> KkIcon.WEAPONS_ENTROPY_FIELD
    WeaponId.SINGULARITY_SPEAR -> KkIcon.WEAPONS_SINGULARITY_SPEAR
    WeaponId.PRISM_RELAY -> KkIcon.WEAPONS_PRISM_RELAY
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

/**
 * Enemy outlines on the 24-unit icon grid (`HUD.dc.html`): the five basic shapes of the boards,
 * the elite octagon and a split diamond. The board's dart is drawn as the pentagon (SPEC hard rule 3).
 */
internal enum class EnemySilhouette(vararg contours: FloatArray) {
    TRIANGLE(floatArrayOf(12f, 3f, 21f, 20f, 3f, 20f)),
    SQUARE(floatArrayOf(4f, 4f, 20f, 4f, 20f, 20f, 4f, 20f)),
    PENTAGON(floatArrayOf(12f, 3f, 21f, 10f, 17.5f, 21f, 6.5f, 21f, 3f, 10f)),
    DIAMOND(floatArrayOf(12f, 2f, 22f, 12f, 12f, 22f, 2f, 12f)),
    HEXAGON(floatArrayOf(7f, 3f, 17f, 3f, 22f, 12f, 17f, 21f, 7f, 21f, 2f, 12f)),
    OCTAGON(floatArrayOf(7.2f, 1f, 16.8f, 1f, 23f, 7.2f, 23f, 16.8f, 16.8f, 23f, 7.2f, 23f, 1f, 16.8f, 1f, 7.2f)),
    SPLIT_DIAMOND(floatArrayOf(12f, 2f, 21.5f, 11f, 2.5f, 11f), floatArrayOf(2.5f, 13f, 21.5f, 13f, 12f, 22f)),
    ;

    /** Closed polygons as x, y pairs on the 24 grid, centered at (12, 12). */
    val contours: List<FloatArray> = contours.toList()
}

/** How each enemy type reads in the world; the Architect has its own frame (see drawEnemy). */
internal fun enemySilhouette(type: EnemyType): EnemySilhouette = when (type) {
    EnemyType.DRIFTER -> EnemySilhouette.TRIANGLE
    EnemyType.SHOOTER -> EnemySilhouette.SQUARE
    EnemyType.CHARGER -> EnemySilhouette.PENTAGON
    EnemyType.INTERCEPTOR -> EnemySilhouette.DIAMOND
    EnemyType.WEAVER -> EnemySilhouette.HEXAGON
    EnemyType.WARDEN -> EnemySilhouette.OCTAGON
    EnemyType.SPLITTER -> EnemySilhouette.SPLIT_DIAMOND
    EnemyType.ELITE -> EnemySilhouette.OCTAGON
    EnemyType.ARCHITECT -> EnemySilhouette.TRIANGLE
}

/** Only shooters carry the threat core dot (`HUD.dc.html`: squares with a centered dot). */
internal fun enemyHasCoreDot(type: EnemyType): Boolean = type == EnemyType.SHOOTER

/**
 * Light arena theme per rebirth tier (`tokens.json rebirthTiers`): grid tint and halftone color
 * only. Tier 0 keeps the neutral board look; tier 10 is black and white.
 */
internal data class ArenaTheme(val grid: Color, val halftone: Color)

private val NeutralArena = ArenaTheme(grid = Kk.Bone.copy(alpha = 0.045f), halftone = Kk.Bone.copy(alpha = 0.05f))
private val ArenaThemes: List<ArenaTheme> = List(KkRebirthTiers.MaxTier + 1) { tier ->
    val accent = KkRebirthTiers.color(tier)
    when {
        tier == 0 -> NeutralArena
        KkRebirthTiers.isEventHorizon(tier) -> ArenaTheme(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.08f))
        else -> ArenaTheme(accent.copy(alpha = 0.11f), accent.copy(alpha = 0.09f))
    }
}

internal fun arenaTheme(rebirthLevel: Int): ArenaTheme = ArenaThemes[rebirthLevel.coerceIn(0, KkRebirthTiers.MaxTier)]
