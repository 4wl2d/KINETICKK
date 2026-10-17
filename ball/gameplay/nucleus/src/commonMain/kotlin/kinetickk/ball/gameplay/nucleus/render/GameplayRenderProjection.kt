// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.render

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kotlin.math.sqrt

enum class GamePhase { RUNNING, PAUSED, CHOICE, GAME_OVER, VICTORY }

/** Content owns the roster; gameplay keeps this name so render and simulation code read unchanged. */
typealias EnemyType = kinetickk.ball.content.api.EnemyType

enum class PickupType { DATA, KEY, REPAIR, RELIC }
/** A hostile [BOLT] flies; a [MINE] stays put, arms and detonates when the Core comes close. */
enum class ProjectileKind { BOLT, MINE }
enum class ChoiceType { ITEM, TOTEM, WEAPON, RELIC, RELIC_BIND }
enum class TotemAction { AMPLIFY_CURRENT, CHANGE_WEAPON }
enum class RelicChoiceAction { ACQUIRE, MELD, REPLACE, MELD_TARGET, DECLINE }
enum class WeaponNodeType { GRAVITY_MINE }
/**
 * Weapon-owned world objects ([WeaponEntityProjection]): Gyre discs, sentries, lenses and their wells,
 * rifts, Aegis shards, missiles, toll and pulse rings, tethers and solar beams.
 */
enum class WeaponEntityKind { DISC, SENTRY, LENS, WELL, RIFT, SHARD, MISSILE, RING, TETHER, BEAM }
/** World obstacles: an [ASTEROID] hurts at speed, a [BUMPER] throws the Core back, an [UNSTABLE] asteroid explodes. */
enum class ObstacleKind { ASTEROID, BUMPER, UNSTABLE }

data class ChoiceOption(
    val type: ChoiceType,
    val title: String,
    val description: String,
    val tag: String,
    val itemId: Int? = null,
    val weaponId: WeaponId? = null,
    val totemAction: TotemAction? = null,
    val relicId: RelicId? = null,
    val relicAction: RelicChoiceAction? = null,
    val relicSlot: Int? = null,
    val rewardFocus: kinetickk.ball.content.api.RewardFocus? = null,
    /** Reborn cards are stronger and count double for resonance; rolled when the offer was built. */
    val reborn: Boolean = false,
    /** Part of the run-opening Heirloom offer. */
    val heirloom: Boolean = false,
)

data class CharacterLatticePoint(val x: Float, val y: Float)

/** A Hive drone at its current and previous fixed-step positions; [darting] while it strikes. */
data class CharacterDroneProjection(
    val x: Float,
    val y: Float,
    val previousX: Float,
    val previousY: Float,
    val darting: Boolean,
)

/** A Crescent void scar from ([x0], [y0]) to ([x1], [y1]); [fuse] fills 0..1 until it detonates, [fade] after. */
data class CharacterScarProjection(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val fuse: Float,
    val fade: Float,
)

/** What renderers and the HUD need of the active character's mechanic; fields of other mechanics keep defaults. */
data class CharacterAbilityProjection(
    /** Mechanic readiness 0..1 (Orbit charge, facets, parry, lattice fill, Stellar Mass, charges, drones, streak). */
    val charge: Float = 0f,
    /** Halo radius (Ring). */
    val ringRadius: Float = 55f,
    val parryWindow: Float = 0f,
    val lattice: ImmutableList<CharacterLatticePoint> = kinetickk.foundation.collections.immutableListOf(),
    /** Intact Prism facets and the regeneration progress toward the next (0..1). */
    val facets: Int = 0,
    val facetProgress: Float = 0f,
    /** Diamond Edge stacks. */
    val edge: Int = 0,
    /** Star Stellar Mass and the Supernova flash (1 fading to 0). */
    val stellarMass: Int = 0,
    val novaFlash: Float = 0f,
    /** Halo Burst flash (1 fading to 0). */
    val haloBurstFlash: Float = 0f,
    /** Dipole polarity: 0 attract, 1 repel, and the flip flash. */
    val polarity: Int = 0,
    val polarityFlash: Float = 0f,
    /** Pentagon beat period in seconds (0 for other characters; beats fall on multiples of it) and the streak. */
    val beatPeriod: Float = 0f,
    val beatWindow: Float = 0f,
    val beatStreak: Int = 0,
    /** Crescent Blink charges and the recharge of the next one (0..1). */
    val blinkCharges: Int = 0,
    val blinkRecharge: Float = 0f,
    val scars: ImmutableList<CharacterScarProjection> = kinetickk.foundation.collections.immutableListOf(),
    val drones: ImmutableList<CharacterDroneProjection> = kinetickk.foundation.collections.immutableListOf(),
    /** Spiral echo ghost: shown while [ghostActive], at its current and previous fixed-step positions. */
    val ghostActive: Boolean = false,
    val ghostX: Float = 0f,
    val ghostY: Float = 0f,
    val previousGhostX: Float = 0f,
    val previousGhostY: Float = 0f,
)

data class PointOfInterestProjection(
    val kind: kinetickk.ball.content.api.PointOfInterestKind,
    val name: String,
    val x: Float,
    val y: Float,
    val active: Boolean,
    val remaining: Float,
    val nextBeacon: Int,
    val progress: Float,
    val defenderIds: ImmutableList<Int>,
    val warningRemaining: Float,
    val volleyAngle: Float,
)

data class EnemyProjection(
    val id: Int,
    val type: EnemyType,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val hp: Float,
    val maxHp: Float,
    val radius: Float,
    val actionTimer: Float,
    val flash: Float,
    val contactCooldown: Float,
    val weaponCooldown: Float,
    val previousX: Float,
    val previousY: Float,
    val dead: Boolean,
    /** Seven 4-bit stack counts in StatusElement order (bits 0..27), then frozen, brittle, embrittled and primed flags. */
    val statusBits: Int = 0,
    /** Aspect, weakness, frame and Architect shift state; decode with [EnemyAffinity]. */
    val affinityBits: Int = 0,
    /** Type-specific behavior step; see [EnemyBehavior]. */
    val behavior: Int = 0,
    /** Seconds left in [behavior] (telegraphs fill as it runs out). */
    val behaviorRemaining: Float = 0f,
    /** Aim or shield direction in radians. */
    val facing: Float = 0f,
    /** Id of the Aegis tethering this enemy, or -1. */
    val linkedTo: Int = -1,
    /** True while a Herald's shield holds. */
    val shielded: Boolean = false,
)

/** Behavior steps published in [EnemyProjection.behavior]; meaning depends on the enemy type. */
object EnemyBehavior {
    const val IDLE: Int = 0
    /** Charger and Phantom strike, Lancer lance. */
    const val STRIKE: Int = 1
    /** Lancer line and Sniper sight line before the attack; Phantom wind-up after it reappears. */
    const val TELEGRAPH: Int = 2
    /** Lancer recovery after a lance. */
    const val RECOVERY: Int = 3
    /** Phantom out of phase: untargetable. */
    const val PHASED: Int = 4
    /** Bomber about to explode. */
    const val PRIMED: Int = 5
}

data class ProjectileProjection(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val life: Float,
    val hostile: Boolean,
    val damage: Float,
    val pierce: Int,
    val colorIndex: Int,
    val sourceWeapon: WeaponId?,
    val previousX: Float,
    val previousY: Float,
    val kind: ProjectileKind = ProjectileKind.BOLT,
)

data class PickupProjection(
    val type: PickupType,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val previousX: Float,
    val previousY: Float,
)

/** A static world obstacle; [id] is stable across rebases, [shapeSeed] picks its outline. */
data class ObstacleProjection(
    val id: Long,
    val kind: ObstacleKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val shapeSeed: Int,
)

data class TrailPointProjection(val x: Float, val y: Float, val age: Float)
data class TotemProjection(val x: Float, val y: Float, val pulse: Float)

data class WeaponNodeProjection(
    val type: WeaponNodeType,
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val radius: Float,
)

data class WeaponOrbitalProjection(
    val index: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val previousX: Float = x,
    val previousY: Float = y,
)

/**
 * A weapon entity at its current and previous fixed-step positions. [endX]/[endY] is a segment's second
 * point (rift, tether anchor); [angle] a beam's or disc's heading; [extent] a beam's length; [phase] the
 * kind's step (a returning disc, a launched shard, a ring's variant).
 */
data class WeaponEntityProjection(
    val kind: WeaponEntityKind,
    val x: Float,
    val y: Float,
    val previousX: Float,
    val previousY: Float,
    val endX: Float,
    val endY: Float,
    val previousEndX: Float,
    val previousEndY: Float,
    val angle: Float,
    val previousAngle: Float,
    val radius: Float,
    val extent: Float,
    val life: Float,
    val maxLife: Float,
    val phase: Int,
)

/**
 * What links the equipped weapon to the build right now (SPEC 10.2): points in its two tags and the
 * power they add, whether the character is its signature, the bound Relic carrying its key, the element
 * its hits apply or adopt, its scaling multiplier and how many of its three mastery forms are open
 * ([activeMasteryForm] is the latest one's content text).
 */
data class WeaponSynergyProjection(
    val tagPoints: Int = 0,
    val tagBonus: Float = 0f,
    val signatureActive: Boolean = false,
    val harmonizingRelic: RelicId? = null,
    val infusionElement: kinetickk.ball.content.api.StatusElement? = null,
    val scalingMultiplier: Float = 1f,
    val masteryForms: Int = 0,
    val activeMasteryForm: String? = null,
)

/** Immutable, API-owned projection of a committed GameplayRun frame. */
class GameplayRenderModel(
    val content: GameplayContentSnapshot,
    val phase: GamePhase,
    val settings: PlayerPreferences,
    val rebirthLevel: Int,
    val screenWidth: Float,
    val screenHeight: Float,
    val uiScale: Float,
    val coreX: Float,
    val coreY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val cameraX: Float,
    val cameraY: Float,
    val pointerX: Float,
    val pointerY: Float,
    val pointerActive: Boolean,
    val braking: Boolean,
    val elapsed: Float,
    val heat: Float,
    val overheated: Boolean,
    val dashPhaseTime: Float,
    val hp: Float,
    val maxHp: Float,
    val shield: Float,
    val maxShield: Float,
    val level: Int,
    val data: Int,
    val nextLevelData: Int,
    val keys: Int,
    val kills: Int,
    val runStatistics: RunStatistics = RunStatistics(),
    val combo: Int,
    val comboTime: Float,
    val runMatter: Long,
    val totalMatter: Long,
    val lastImpact: Float,
    val lastImpactTime: Float,
    val damageFlash: Float,
    val runGrace: Float,
    val screenShake: Float,
    val message: String,
    val messageTime: Float,
    val mass: Float,
    val damageMultiplier: Float,
    val weaponPower: Float,
    val coolingRate: Float,
    val magnetStrength: Float,
    val dashImpulse: Float,
    val dashHeatCost: Float,
    val regenPerSecond: Float,
    val critChance: Float,
    val critMultiplier: Float,
    val pickupRadius: Float,
    val luck: Float,
    val dataGain: Float,
    val matterGain: Float,
    val attackSpeed: Float,
    val damageReduction: Float,
    val comboWindow: Float,
    val overdriveGain: Float,
    val dragCoefficient: Float,
    val polarityStability: Float,
    val weapon: WeaponId,
    val weaponLevel: Int,
    val overdriveCharge: Float,
    val overdriveTime: Float,
    val rerollsRemaining: Int,
    val acquiredItemCount: Int,
    val recentItem: ItemDefinition?,
    val equippedRelics: ImmutableList<EquippedRelic>,
    val morningstarAngle: Float,
    val morningstarX: Float,
    val morningstarY: Float,
    val weaponBeamTime: Float,
    val weaponBeamStartX: Float,
    val weaponBeamStartY: Float,
    val weaponBeamEndX: Float,
    val weaponBeamEndY: Float,
    val totem: TotemProjection?,
    val coreShape: CoreShape,
    val enemies: ImmutableList<EnemyProjection>,
    val projectiles: ImmutableList<ProjectileProjection>,
    val pickups: ImmutableList<PickupProjection>,
    val trail: ImmutableList<TrailPointProjection>,
    val weaponNodes: ImmutableList<WeaponNodeProjection>,
    val weaponOrbitals: ImmutableList<WeaponOrbitalProjection>,
    val choices: ImmutableList<ChoiceOption>,
    val choiceType: ChoiceType,
    val pendingRelicChoiceCount: Int,
    internal val itemStacks: ImmutableList<Int>,
    internal val discoveredItemIds: ImmutableSet<Int>,
    internal val relicRanks: ImmutableList<Int>,
    val pointsOfInterest: ImmutableList<PointOfInterestProjection> = kinetickk.foundation.collections.immutableListOf(),
    val directedChoice: Boolean = false,
    val characterAbility: CharacterAbilityProjection = CharacterAbilityProjection(),
    val effectiveWeaponPower: Float = weaponPower,
    val rewardPreviews: ImmutableList<RewardPreview> = kinetickk.foundation.collections.immutableListOf(),
    internal val discoveredRelicMask: Long = 0L,
    val build: BuildProjection = BuildProjection(),
    internal val discoveredReactionMask: Int = 0,
    internal val discoveredPairingMask: Int = 0,
    /** Current Core radius; renderers draw the Core with it. [CORE_RADIUS] is only the default. */
    val coreRadius: Float = CORE_RADIUS,
    /** Power of character abilities and weapon-based item effects; reward cards price weapon units with it. */
    val abilityPower: Float = effectiveWeaponPower,
    /**
     * Accumulated real time past the last simulated state as a fraction of [FIXED_STEP] (0..1).
     * Renderers draw moving things at `previous + (current - previous) * alpha`; it is 1 whenever
     * the run is not running, so paused and terminal frames show the exact state, and until the
     * first step after a run starts or resumes (its previous positions are older than one step).
     */
    val interpolationAlpha: Float = 1f,
    /** Core position at the start of the last fixed step. */
    val previousCoreX: Float = coreX,
    val previousCoreY: Float = coreY,
    /** Camera position at the start of the last fixed step. */
    val previousCameraX: Float = cameraX,
    val previousCameraY: Float = cameraY,
    /** Morningstar position at the start of the last fixed step. */
    val previousMorningstarX: Float = morningstarX,
    val previousMorningstarY: Float = morningstarY,
    /**
     * Device pixels per dp of the viewport the simulation accepted. Simulation coordinates (world,
     * viewport and pointer) are dp; the world layer is drawn scaled by this density.
     */
    val viewportDensity: Float = 1f,
    /** World obstacles of the generated window around the Core (static; rebases move them). */
    val obstacles: ImmutableList<ObstacleProjection> = kinetickk.foundation.collections.immutableListOf(),
    /** World distance from the Core to the minimap's edge (dp). */
    val minimapRange: Float = DEFAULT_MINIMAP_RANGE,
    /** Weapon-owned world objects with their previous fixed-step positions. */
    val weaponEntities: ImmutableList<WeaponEntityProjection> = kinetickk.foundation.collections.immutableListOf(),
    /** The equipped weapon's live synergies; the pause build screen reads them. */
    val weaponSynergy: WeaponSynergyProjection = WeaponSynergyProjection(),
) {
    val speed: Float get() = vectorLength(velocityX, velocityY)
    val runProgress: Float get() = content.tempo.progress(elapsed)
    val tetherDistance: Float
        get() {
            val targetX = cameraX + pointerX - screenWidth * 0.5f
            val targetY = cameraY + pointerY - screenHeight * 0.5f
            return vectorLength(targetX - coreX, targetY - coreY)
        }
    val dashReady: Boolean get() = !overheated && heat <= MAX_HEAT - dashHeatCost * 0.5f
    val tetherAuthority: Float get() = polarityStability * polarityStability
    val velocityTier: Int
        get() = when {
            speed >= 2_200f -> 4
            speed >= 1_400f -> 3
            speed >= 900f -> 2
            speed >= 500f -> 1
            else -> 0
        }
    val discoveredItemCount: Int get() = discoveredItemIds.size
    fun isItemDiscovered(id: Int): Boolean = id in discoveredItemIds
    fun isRelicDiscovered(id: kinetickk.ball.content.api.RelicId): Boolean = discoveredRelicMask and (1L shl id.ordinal) != 0L
    fun isReactionDiscovered(id: kinetickk.ball.content.api.ReactionId): Boolean = discoveredReactionMask and (1 shl id.ordinal) != 0
    fun isPairingDiscovered(id: kinetickk.ball.content.api.RelicPairingId): Boolean = discoveredPairingMask and (1 shl id.ordinal) != 0
    val currentWeaponDefinition: WeaponDefinition get() = content.weapon(weapon)
    val currentWeaponMastery: WeaponMastery get() = content.weaponMasteryForLevel(weaponLevel)
    val nextWeaponMastery: WeaponMastery? get() = content.weaponMasteryAfter(weaponLevel)
    val weaponMasteryProgress: Float
        get() {
            val current = currentWeaponMastery
            val next = nextWeaponMastery ?: return 1f
            return ((weaponLevel - current.minimumLevel).toFloat() /
                (next.minimumLevel - current.minimumLevel)).coerceIn(0f, 1f)
        }
    val choicesCanReroll: Boolean
        get() = phase == GamePhase.CHOICE && !directedChoice && rerollsRemaining > 0 && when (choiceType) {
            ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> true
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> false
        }
    val itemStacksSnapshot: ImmutableList<Int> get() = itemStacks

    fun relicRank(id: RelicId): Int = relicRanks.getOrElse(id.ordinal) { 0 }
    fun itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }

    companion object {
        const val MAX_HEAT = 100f
        const val CORE_RADIUS = 16f
        const val FIXED_STEP = 1f / 120f
        const val DEFAULT_MINIMAP_RANGE = 2_200f
    }
}

private fun vectorLength(x: Float, y: Float): Float = sqrt(x * x + y * y)
