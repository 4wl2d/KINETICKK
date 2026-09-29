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
import kotlin.math.sin

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
 * Silhouettes that are one shape turned by 45 degrees (the shooter's square, the interceptor's
 * diamond): they keep the board orientation and only wobble, or a turning diamond would read as a
 * square half of the time.
 */
internal fun holdsBoardOrientation(silhouette: EnemySilhouette): Boolean =
    silhouette == EnemySilhouette.SQUARE || silhouette == EnemySilhouette.DIAMOND

/** Per-id lean of a [holdsBoardOrientation] silhouette, either way (the board's squares sit at 12 and -8 degrees). */
private const val ENEMY_LEAN_DEGREES = 9

/** Slow wobble of a [holdsBoardOrientation] silhouette around its lean, either way. */
private const val ENEMY_WOBBLE_DEGREES = 3f

/** Largest tilt of a [holdsBoardOrientation] silhouette: its lean plus its wobble, in degrees. */
internal const val ENEMY_WOBBLE_MAX_DEGREES = ENEMY_LEAN_DEGREES + ENEMY_WOBBLE_DEGREES

/**
 * Enemy rotation in degrees at [elapsed] seconds (`HUD.dc.html` enemy table): the elite spins once
 * per 20 s (SPEC 7.2); the square and the diamond keep the board orientation with a per-id lean and
 * a slow wobble, at most [ENEMY_WOBBLE_MAX_DEGREES] either way; the other shapes read the same at
 * any angle and turn slowly, their phase offset by the id. The Architect draws its own frames.
 */
internal fun enemyRotation(type: EnemyType, elapsed: Float, id: Int): Float {
    if (holdsBoardOrientation(enemySilhouette(type))) {
        val span = 2 * ENEMY_LEAN_DEGREES + 1
        val lean = ((id * 37) % span + span) % span - ENEMY_LEAN_DEGREES
        return lean + ENEMY_WOBBLE_DEGREES * sin(elapsed * 0.9f + id)
    }
    val degreesPerSecond = when (type) {
        EnemyType.DRIFTER -> 11f
        EnemyType.CHARGER -> 6f
        EnemyType.WEAVER -> 12f
        EnemyType.WARDEN -> -5f
        EnemyType.SPLITTER -> 7f
        EnemyType.ELITE -> 360f / 20f
        EnemyType.SHOOTER, EnemyType.INTERCEPTOR, EnemyType.ARCHITECT -> 0f // held above; the Architect turns its own frames
    }
    return elapsed * degreesPerSecond + (id * 37 % 360)
}

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
