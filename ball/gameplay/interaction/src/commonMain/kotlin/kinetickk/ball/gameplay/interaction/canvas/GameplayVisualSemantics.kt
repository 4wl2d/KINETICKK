// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.SkillId
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
 * and relic procs, 3 ram impact, elite kill and key pickups, 4 damage taken (the only threat one),
 * 8..14 an element's ring (Breaks and reactions), 15 the Apex gold, 16..22 an element's jagged
 * quake ring ([isQuakeColor]), 23 Baby mode's soft rings and sparks ([SOFT_RING_COLOR]).
 */
internal fun fxColor(colorIndex: Int, roles: KkRolePalette): Color = when (colorIndex) {
    0, 3 -> roles.you
    4 -> roles.threat
    2 -> Kk.Bone2
    in ELEMENT_RING_COLORS -> roles.element(colorIndex - ELEMENT_RING_COLORS.first)
    APEX_GOLD_COLOR -> roles.gold.base
    in QUAKE_RING_COLORS -> roles.element(colorIndex - QUAKE_RING_COLORS.first)
    SOFT_RING_COLOR -> roles.soft
    else -> Kk.Bone
}

/** Element ring colour indices (8 + element), the Apex gold (15) and element quake rings (16 + element). */
internal val ELEMENT_RING_COLORS = 8..14
internal const val APEX_GOLD_COLOR = 15
internal val QUAKE_RING_COLORS = 16..22

/** Baby mode's soft rings and sparks (nucleus EASY_SOFT_COLOR_INDEX). */
internal const val SOFT_RING_COLOR = 23

/** Shockwaves of these colour indices draw as a jagged quake ring. */
internal fun isQuakeColor(colorIndex: Int): Boolean = colorIndex in QUAKE_RING_COLORS

/** Damage numbers take the hue of their element (the preset's volt for Kinetic); the tier shows only as size. */
internal fun damageNumberColor(tone: Int, roles: KkRolePalette): Color = roles.tone(tone)

/** Size of each tier relative to the standard number (steps ×1, ×1.2, ×1.45, ×1.8). */
internal fun damageNumberScale(tier: DamageNumberTier): Float = when (tier) {
    DamageNumberTier.STANDARD -> 1f
    DamageNumberTier.STRONG -> 1.2f
    DamageNumberTier.POWERFUL -> 1.45f
    DamageNumberTier.DEVASTATING -> 1.8f
}

/** Status elements in StatusElement order (thermal, cryo, ion, gravitic, entropy, sonic, void). */
internal const val STATUS_ELEMENT_COUNT = 7

private val ElementFills = arrayOf(
    KkFillKind.SHIMMER, KkFillKind.LATTICE, KkFillKind.ZIGZAG, KkFillKind.RINGS, KkFillKind.PITTING, KkFillKind.BARS,
    KkFillKind.CROSSES,
)

/** The pattern that carries an element's identity without its hue (color vision presets). */
internal fun elementFill(element: Int): KkFillKind = ElementFills[element.coerceIn(0, STATUS_ELEMENT_COUNT - 1)]

/** The element's glyph (`icons.json aspects.*`, Kinetic and Sovereign excluded). */
internal fun elementIcon(element: Int): KkIcon = KkIcon.Elements[element.coerceIn(0, STATUS_ELEMENT_COUNT - 1)]

/**
 * Reader of [kinetickk.ball.gameplay.nucleus.render.EnemyProjection.statusBits]: seven 4-bit stack
 * counts in element order (bits 0..27), then the Frozen, Brittle, Embrittled and primed flags in bits
 * 28..31. Pure bit work, no allocation.
 */
internal object EnemyStatusBits {
    private const val FLAGS_SHIFT = 4 * STATUS_ELEMENT_COUNT

    const val FROZEN = 1 shl FLAGS_SHIFT
    const val BRITTLE = 1 shl (FLAGS_SHIFT + 1)
    const val EMBRITTLED = 1 shl (FLAGS_SHIFT + 2)

    /** The seven stack counts. */
    const val STACK_BITS = (1 shl FLAGS_SHIFT) - 1

    /** Some element sits one stack short of its Break (the sign bit). */
    const val PRIMED = 1 shl (FLAGS_SHIFT + 3)

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
    WeaponId.GYRE_DISC -> KkIcon.WEAPONS_GYRE_DISC
    WeaponId.SCATTER_BLOOM -> KkIcon.WEAPONS_SCATTER_BLOOM
    WeaponId.TETHER_ARC -> KkIcon.WEAPONS_TETHER_ARC
    WeaponId.SENTRY_GRID -> KkIcon.WEAPONS_SENTRY_GRID
    WeaponId.HUNTER_SALVO -> KkIcon.WEAPONS_HUNTER_SALVO
    WeaponId.CONCUSSION_ENGINE -> KkIcon.WEAPONS_CONCUSSION_ENGINE
    WeaponId.GLACIER_BELL -> KkIcon.WEAPONS_GLACIER_BELL
    WeaponId.EVENT_LENS -> KkIcon.WEAPONS_EVENT_LENS
    WeaponId.SOLAR_SWEEP -> KkIcon.WEAPONS_SOLAR_SWEEP
    WeaponId.AEGIS_SHARDS -> KkIcon.WEAPONS_AEGIS_SHARDS
    WeaponId.HOLLOW_RIFT -> KkIcon.WEAPONS_HOLLOW_RIFT
    WeaponId.RESONANCE_DRUM -> KkIcon.WEAPONS_RESONANCE_DRUM
}

/**
 * The glyph of an active effect (all 18 distinct): the one mark of an active item wherever it shows, the HUD's Z/X/C
 * slot, the reward card's icon plate and watermark, and the other screens (the pause Build grid, the Codex). Public
 * so those screens draw an active the same way.
 */
fun skillGlyph(id: SkillId): KkIcon = when (id) {
    SkillId.SOLAR_FLARE -> KkIcon.WEAPONS_SOLAR_SWEEP
    SkillId.METEOR -> KkIcon.WEAPONS_MORNINGSTAR
    SkillId.FROST_LINE -> KkIcon.WEAPONS_PHASE_LATTICE
    SkillId.FROST_SNAP -> KkIcon.WEAPONS_GLACIER_BELL
    SkillId.ARC_HOOK -> KkIcon.WEAPONS_TETHER_ARC
    SkillId.STORM_COIL -> KkIcon.WEAPONS_ARC_COIL
    SkillId.GRAVITY_WELL -> KkIcon.WEAPONS_GRAVITY_MINES
    SkillId.MASS_DRIVER -> KkIcon.WEAPONS_SINGULARITY_SPEAR
    SkillId.DECAY_SPORES -> KkIcon.WEAPONS_SCATTER_BLOOM
    SkillId.ENTROPIC_TIDE -> KkIcon.WEAPONS_ENTROPY_FIELD
    SkillId.SONIC_BOOM -> KkIcon.WEAPONS_RESONANCE_DRUM
    SkillId.RESONATOR -> KkIcon.WEAPONS_SENTRY_GRID
    SkillId.VOID_STEP -> KkIcon.WEAPONS_HOLLOW_RIFT
    SkillId.NULL_SHROUD -> KkIcon.WEAPONS_EVENT_LENS
    SkillId.SLINGSHOT -> KkIcon.WEAPONS_GYRE_DISC
    SkillId.FULL_STOP -> KkIcon.WEAPONS_CONCUSSION_ENGINE
    SkillId.CATALYZE -> KkIcon.WEAPONS_PRISM_RELAY
    SkillId.DUAL_INFUSION -> KkIcon.WEAPONS_FLUX_WAKE
}

/**
 * Enemy outlines on the 24-unit icon grid (`HUD.dc.html`): the five basic shapes of the boards,
 * the elite octagon and a split diamond, then the 0.4.0 and 0.6.0 rosters' outlines. The board's dart is drawn
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
    /** Comet: a capsule streaking along its lane. */
    PILL(floatArrayOf(9f, 1f, 15f, 1f, 18f, 6f, 18f, 18f, 15f, 23f, 9f, 23f, 6f, 18f, 6f, 6f)),
    /** Pylon: a turret mast on its mount. */
    TOWER(floatArrayOf(9f, 2f, 15f, 2f, 15f, 9f, 19f, 9f, 21f, 22f, 3f, 22f, 5f, 9f, 9f, 9f)),
    /** Sentry: a low dome on its wall. */
    BUNKER(floatArrayOf(2f, 19f, 2f, 14f, 5f, 7f, 12f, 4f, 19f, 7f, 22f, 14f, 22f, 19f)),
    /** Crawler: a many-legged hull. */
    CRAB(floatArrayOf(4f, 8f, 8f, 5f, 16f, 5f, 20f, 8f, 23f, 13f, 19f, 12f, 18f, 18f, 14f, 16f, 10f, 16f, 6f, 18f, 5f, 12f, 1f, 13f)),
    /** Seraph: spread wings. */
    WINGS(floatArrayOf(12f, 3f, 15f, 9f, 22f, 5f, 20f, 14f, 14f, 14f, 12f, 21f, 10f, 14f, 4f, 14f, 2f, 5f, 9f, 9f)),
    /** Choir: a bell. */
    BELL(floatArrayOf(8f, 3f, 16f, 3f, 18f, 12f, 22f, 18f, 22f, 21f, 2f, 21f, 2f, 18f, 6f, 12f)),
    /** Imp: a horned head. */
    HORNS(floatArrayOf(5f, 2f, 9f, 8f, 15f, 8f, 19f, 2f, 21f, 11f, 18f, 21f, 6f, 21f, 3f, 11f)),
    /** Hound: a jaw. */
    MAW(floatArrayOf(5f, 3f, 12f, 8f, 19f, 3f, 22f, 12f, 17f, 21f, 7f, 21f, 2f, 12f)),
    /** Cipher: an offset glyph. */
    GLYPH(floatArrayOf(3f, 3f, 13f, 3f, 13f, 7f, 21f, 7f, 21f, 21f, 11f, 21f, 11f, 17f, 3f, 17f)),
    /** Mirror: an hourglass. */
    HOURGLASS(floatArrayOf(3f, 3f, 21f, 3f, 14f, 12f, 21f, 21f, 3f, 21f, 10f, 12f)),
    /** Warden Prime: an eight-point star. */
    OCTAGRAM(floatArrayOf(12f, 1f, 14.8f, 5.3f, 19.8f, 4.2f, 18.7f, 9.2f, 23f, 12f, 18.7f, 14.8f, 19.8f, 19.8f, 14.8f, 18.7f, 12f, 23f, 9.2f, 18.7f, 4.2f, 19.8f, 5.3f, 14.8f, 1f, 12f, 5.3f, 9.2f, 4.2f, 4.2f, 9.2f, 5.3f)),
    /** Brood Queen: a crown. */
    CROWN(floatArrayOf(2f, 21f, 2f, 7f, 7f, 12f, 12f, 3f, 17f, 12f, 22f, 7f, 22f, 21f)),
    /** Throne: an upright seat. */
    THRONE(floatArrayOf(5f, 2f, 19f, 2f, 19f, 12f, 22f, 12f, 22f, 22f, 2f, 22f, 2f, 12f, 5f, 12f)),
    /** Pyre Herald: a five-point star. */
    PENTAGRAM(floatArrayOf(12f, 1f, 14.8f, 8.1f, 22.5f, 8.6f, 16.6f, 13.5f, 18.5f, 20.9f, 12f, 16.8f, 5.5f, 20.9f, 7.4f, 13.5f, 1.5f, 8.6f, 9.2f, 8.1f)),
    /** Cartographer: a compass rose. */
    COMPASS(floatArrayOf(12f, 1f, 13.4f, 8.7f, 17.4f, 6.6f, 15.3f, 10.6f, 23f, 12f, 15.3f, 13.4f, 17.4f, 17.4f, 13.4f, 15.3f, 12f, 23f, 10.6f, 15.3f, 6.6f, 17.4f, 8.7f, 13.4f, 1f, 12f, 8.7f, 10.6f, 6.6f, 6.6f, 10.6f, 8.7f)),
    /** Antecedent: a heptagon around an inverted triangle. */
    SIGIL(floatArrayOf(12f, 1f, 20.6f, 5.1f, 22.7f, 14.4f, 16.8f, 21.9f, 7.2f, 21.9f, 1.3f, 14.4f, 3.4f, 5.1f), floatArrayOf(12f, 17.5f, 7.2f, 9.2f, 16.8f, 9.2f)),
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
    EnemyType.COMET -> EnemySilhouette.PILL
    EnemyType.PYLON -> EnemySilhouette.TOWER
    EnemyType.SENTRY -> EnemySilhouette.BUNKER
    EnemyType.CRAWLER -> EnemySilhouette.CRAB
    EnemyType.SERAPH -> EnemySilhouette.WINGS
    EnemyType.CHOIR -> EnemySilhouette.BELL
    EnemyType.IMP -> EnemySilhouette.HORNS
    EnemyType.HOUND -> EnemySilhouette.MAW
    EnemyType.CIPHER -> EnemySilhouette.GLYPH
    EnemyType.MIRROR -> EnemySilhouette.HOURGLASS
    EnemyType.WARDEN_PRIME -> EnemySilhouette.OCTAGRAM
    EnemyType.BROOD_QUEEN -> EnemySilhouette.CROWN
    EnemyType.THRONE -> EnemySilhouette.THRONE
    EnemyType.PYRE_HERALD -> EnemySilhouette.PENTAGRAM
    EnemyType.CARTOGRAPHER -> EnemySilhouette.COMPASS
    EnemyType.ANTECEDENT -> EnemySilhouette.SIGIL
}

/**
 * Kinds whose outline points along their aim (the Lancer's lane, the Sniper's sight line, the
 * Reflector's shield, the Comet's flyby, the Pylon's and the Sentry's barrels, the Hound's lunge)
 * instead of turning on a clock.
 */
internal fun facesAim(type: EnemyType): Boolean = when (type) {
    EnemyType.LANCER, EnemyType.SNIPER, EnemyType.REFLECTOR, EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY,
    EnemyType.HOUND,
    -> true
    else -> false
}

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
    // The Throne and the Antecedent stand upright.
    if (type == EnemyType.THRONE || type == EnemyType.ANTECEDENT) return 0f
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
        EnemyType.CRAWLER -> 25f
        EnemyType.SERAPH -> 8f
        EnemyType.CHOIR -> -6f
        EnemyType.IMP -> 28f
        EnemyType.MIRROR -> 10f
        EnemyType.WARDEN_PRIME -> -4f
        EnemyType.BROOD_QUEEN -> 3f
        EnemyType.PYRE_HERALD -> 360f / 16f
        EnemyType.CARTOGRAPHER -> 360f / 30f
        EnemyType.CIPHER -> 0f // a glyph that never turns
        EnemyType.COMET, EnemyType.PYLON, EnemyType.SENTRY, EnemyType.HOUND -> 0f // they point along their aim (aimDegrees)
        EnemyType.THRONE, EnemyType.ANTECEDENT -> 0f // upright, returned above
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
