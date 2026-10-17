// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.nucleus.model.DamageNumberTier
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRebirthTiers
import kinetickk.foundation.design.KkRolePalette
import kotlin.math.max
import kotlin.math.PI
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

/** Damage numbers take the hue of their element (the preset's volt for Kinetic); the tier shows only as size. */
internal fun damageNumberColor(tone: Int, roles: KkRolePalette): Color = roles.tone(tone)

/** Size of each tier relative to the standard number (steps ×1, ×1.2, ×1.45, ×1.8). */
internal fun damageNumberScale(tier: DamageNumberTier): Float = when (tier) {
    DamageNumberTier.STANDARD -> 1f
    DamageNumberTier.STRONG -> 1.2f
    DamageNumberTier.POWERFUL -> 1.45f
    DamageNumberTier.DEVASTATING -> 1.8f
}

/** Status elements in StatusElement order (thermal, cryo, ion, gravitic, entropy). */
internal const val STATUS_ELEMENT_COUNT = 5

private val ElementFills = arrayOf(KkFillKind.SHIMMER, KkFillKind.LATTICE, KkFillKind.ZIGZAG, KkFillKind.RINGS, KkFillKind.PITTING)

/** The pattern that carries an element's identity without its hue (color vision presets). */
internal fun elementFill(element: Int): KkFillKind = ElementFills[element.coerceIn(0, STATUS_ELEMENT_COUNT - 1)]

/** The element's glyph (`icons.json aspects.*`, Kinetic and Sovereign excluded). */
internal fun elementIcon(element: Int): KkIcon = KkIcon.Elements[element.coerceIn(0, STATUS_ELEMENT_COUNT - 1)]

/**
 * Reader of [kinetickk.ball.gameplay.nucleus.render.EnemyProjection.statusBits]: five 4-bit stack
 * counts in element order, then the Frozen, Brittle, Embrittled and primed flags. Pure bit work,
 * no allocation.
 */
internal object EnemyStatusBits {
    const val FROZEN = 1 shl 20
    const val BRITTLE = 1 shl 21
    const val EMBRITTLED = 1 shl 22

    /** The five stack counts. */
    const val STACK_BITS = (1 shl 20) - 1

    /** Some element sits one stack short of its Break. */
    const val PRIMED = 1 shl 23

    fun stacks(bits: Int, element: Int): Int = (bits ushr (element * 4)) and 0xF

    /** The element with the most stacks (ties go to element order), or -1 without stacks. */
    fun dominant(bits: Int): Int {
        var best = -1
        var most = 0
        for (element in 0 until STATUS_ELEMENT_COUNT) {
            val count = stacks(bits, element)
            if (count > most) {
                most = count
                best = element
            }
        }
        return best
    }

    /**
     * The other element with the most stacks when it holds at least two: the next hit of either
     * element sets off their reaction. -1 otherwise.
     */
    fun reactionPartner(bits: Int, dominant: Int): Int {
        var best = -1
        var most = 1
        for (element in 0 until STATUS_ELEMENT_COUNT) {
            if (element == dominant) continue
            val count = stacks(bits, element)
            if (count > most) {
                most = count
                best = element
            }
        }
        return best
    }
}

/** The build's Break threshold for [element] (the content's base before the first build frame). */
internal fun GameplayRenderModel.breakThresholdOf(element: Int): Int {
    val built = build.breakThresholds.getOrElse(element) { 0 }
    if (built > 0) return built
    return content.build.elements.getOrNull(element)?.breakThreshold ?: 1
}

/** Cryo's Break comes this many stacks sooner on an Embrittled enemy (the nucleus's `breakThreshold`). */
private const val EMBRITTLED_CRYO_CUT = 2
private const val CRYO = 1

/**
 * The Break threshold of [element] on an enemy carrying [bits]: the build's threshold, cut for
 * Cryo while the enemy is Embrittled, never below the element's floor (as the nucleus reads it).
 */
internal fun GameplayRenderModel.enemyBreakThresholdOf(element: Int, bits: Int): Int {
    val threshold = breakThresholdOf(element)
    if (element != CRYO || bits and EnemyStatusBits.EMBRITTLED == 0) return threshold
    val floor = content.build.elements.getOrNull(element)?.breakFloor ?: 1
    return max(floor, threshold - EMBRITTLED_CRYO_CUT).coerceAtMost(threshold)
}

/**
 * The element one stack short of its Break on an enemy carrying [bits] (what its primed flag
 * means): the dominant one when it is, otherwise the first in element order; -1 when not primed.
 */
internal fun GameplayRenderModel.primedElement(bits: Int, dominant: Int): Int {
    if (bits and EnemyStatusBits.PRIMED == 0) return -1
    if (dominant >= 0 && isOneShort(bits, dominant)) return dominant
    for (element in 0 until STATUS_ELEMENT_COUNT) if (isOneShort(bits, element)) return element
    return -1
}

private fun GameplayRenderModel.isOneShort(bits: Int, element: Int): Boolean {
    val stacks = EnemyStatusBits.stacks(bits, element)
    return stacks > 0 && stacks == enemyBreakThresholdOf(element, bits) - 1
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
 * the elite octagon and a split diamond, then the 0.4.0 roster's outlines. The board's dart is drawn
 * as the pentagon (SPEC hard rule 3). Public so other screens (the Codex) draw enemies the same way.
 */
enum class EnemySilhouette(vararg contours: FloatArray) {
    TRIANGLE(floatArrayOf(12f, 3f, 21f, 20f, 3f, 20f)),
    SQUARE(floatArrayOf(4f, 4f, 20f, 4f, 20f, 20f, 4f, 20f)),
    PENTAGON(floatArrayOf(12f, 3f, 21f, 10f, 17.5f, 21f, 6.5f, 21f, 3f, 10f)),
    DIAMOND(floatArrayOf(12f, 2f, 22f, 12f, 12f, 22f, 2f, 12f)),
    HEXAGON(floatArrayOf(7f, 3f, 17f, 3f, 22f, 12f, 17f, 21f, 7f, 21f, 2f, 12f)),
    OCTAGON(floatArrayOf(7.2f, 1f, 16.8f, 1f, 23f, 7.2f, 23f, 16.8f, 16.8f, 23f, 7.2f, 23f, 1f, 16.8f, 1f, 7.2f)),
    SPLIT_DIAMOND(floatArrayOf(12f, 2f, 21.5f, 11f, 2.5f, 11f), floatArrayOf(2.5f, 13f, 21.5f, 13f, 12f, 22f)),
    /** Swarmling: a small notched dart. */
    DART(floatArrayOf(12f, 2f, 21f, 21f, 12f, 15.5f, 3f, 21f)),
    /** Bomber: a four-point star. */
    STAR(floatArrayOf(12f, 1f, 15f, 9f, 23f, 12f, 15f, 15f, 12f, 23f, 9f, 15f, 1f, 12f, 9f, 9f)),
    /** Lancer: a long blade pointing along its lane. */
    LANCE(floatArrayOf(12f, 1f, 16f, 13f, 12f, 23f, 8f, 13f)),
    /** Sniper: a narrow needle pointing along its sight line. */
    NEEDLE(floatArrayOf(12f, 1f, 17f, 22f, 12f, 19f, 7f, 22f)),
    /** Aegis: a cross. */
    CROSS(floatArrayOf(9f, 2f, 15f, 2f, 15f, 9f, 22f, 9f, 22f, 15f, 15f, 15f, 15f, 22f, 9f, 22f, 9f, 15f, 2f, 15f, 2f, 9f, 9f, 9f)),
    /** Minelayer: a trapezoid. */
    TRAPEZOID(floatArrayOf(6f, 4f, 18f, 4f, 22f, 20f, 2f, 20f)),
    /** Phantom: an inverted triangle. */
    WEDGE(floatArrayOf(3f, 4f, 21f, 4f, 12f, 21f)),
    /** Broodmother: a ten-sided hull. */
    DECAGON(floatArrayOf(12f, 1f, 18.5f, 3.1f, 22.5f, 8.6f, 22.5f, 15.4f, 18.5f, 20.9f, 12f, 23f, 5.5f, 20.9f, 1.5f, 15.4f, 1.5f, 8.6f, 5.5f, 3.1f)),
    /** Reflector: a flat shield front with a rounded back. */
    SHIELD(floatArrayOf(3f, 5f, 21f, 5f, 21f, 12f, 18f, 18f, 12f, 22f, 6f, 18f, 3f, 12f)),
    /** Herald: a six-point star. */
    HEXAGRAM(floatArrayOf(12f, 1f, 15f, 7f, 22f, 7f, 18f, 12f, 22f, 17f, 15f, 17f, 12f, 23f, 9f, 17f, 2f, 17f, 6f, 12f, 2f, 7f, 9f, 7f)),
    ;

    /** Closed polygons as x, y pairs on the 24 grid, centered at (12, 12). */
    val contours: List<FloatArray> = contours.toList()
}

/** How each enemy type reads in the world; the Architect has its own frame (see drawEnemy). */
fun enemySilhouette(type: EnemyType): EnemySilhouette = when (type) {
    EnemyType.DRIFTER -> EnemySilhouette.TRIANGLE
    EnemyType.SHOOTER -> EnemySilhouette.SQUARE
    EnemyType.CHARGER -> EnemySilhouette.PENTAGON
    EnemyType.INTERCEPTOR -> EnemySilhouette.DIAMOND
    EnemyType.WEAVER -> EnemySilhouette.HEXAGON
    EnemyType.WARDEN -> EnemySilhouette.OCTAGON
    EnemyType.SPLITTER -> EnemySilhouette.SPLIT_DIAMOND
    EnemyType.ELITE -> EnemySilhouette.OCTAGON
    EnemyType.ARCHITECT -> EnemySilhouette.TRIANGLE
    EnemyType.SWARMLING -> EnemySilhouette.DART
    EnemyType.BOMBER -> EnemySilhouette.STAR
    EnemyType.LANCER -> EnemySilhouette.LANCE
    EnemyType.SNIPER -> EnemySilhouette.NEEDLE
    EnemyType.AEGIS -> EnemySilhouette.CROSS
    EnemyType.MINELAYER -> EnemySilhouette.TRAPEZOID
    EnemyType.PHANTOM -> EnemySilhouette.WEDGE
    EnemyType.BROODMOTHER -> EnemySilhouette.DECAGON
    EnemyType.REFLECTOR -> EnemySilhouette.SHIELD
    EnemyType.HERALD -> EnemySilhouette.HEXAGRAM
}

/**
 * Kinds whose outline points along their aim (the Lancer's lane, the Sniper's sight line, the
 * Reflector's shield) instead of turning on a clock.
 */
internal fun facesAim(type: EnemyType): Boolean =
    type == EnemyType.LANCER || type == EnemyType.SNIPER || type == EnemyType.REFLECTOR

/** Degrees that turn a grid outline (pointing up) toward [facing] radians. */
internal fun aimDegrees(facing: Float): Float = facing * (180f / PI.toFloat()) + 90f

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
        EnemyType.SWARMLING -> 40f
        EnemyType.BOMBER -> 30f
        EnemyType.AEGIS -> -8f
        EnemyType.MINELAYER -> 14f
        EnemyType.PHANTOM -> 9f
        EnemyType.BROODMOTHER -> 4f
        EnemyType.HERALD -> 360f / 18f
        EnemyType.SHOOTER, EnemyType.INTERCEPTOR, EnemyType.ARCHITECT -> 0f // held above; the Architect turns its own frames
        EnemyType.LANCER, EnemyType.SNIPER, EnemyType.REFLECTOR -> 0f // they point along their aim (aimDegrees)
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
