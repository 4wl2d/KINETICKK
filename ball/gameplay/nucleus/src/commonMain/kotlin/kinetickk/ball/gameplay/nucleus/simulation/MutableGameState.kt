// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery

import kinetickk.foundation.random.CloneableXorWowRandom
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.gameplay.api.*
import kinetickk.ball.gameplay.nucleus.render.*
import kinetickk.ball.gameplay.nucleus.model.*
import kinetickk.ball.gameplay.nucleus.protocol.BoundedVisualFxCueAccumulator
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.immutableSetOf
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.max
import kotlin.math.min
import kotlin.jvm.JvmInline


/**
 * Reducer-owned candidate storage. Each field declares its initial value and fork source together,
 * so extending owned State requires no second field-copy list in the render mapper.
 *
 * Full reductions isolate mutable storage. Scalar input reductions may retain the explicitly
 * stable storage selected by GameReducer; pending output buffers always belong to the candidate.
 */
internal class MutableGameState(
    internal val content: GameplayContentSnapshot,
    seed: Int = 731_991,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
    bootstrapProgress: GameplayProfileSnapshot? = null,
    reductionSource: MutableGameState? = null,
    shareStableReductionStorage: Boolean = false,
) {
    companion object {
        const val CURSOR_KILL_RADIUS = 22f
        const val CORE_RADIUS = 16f
        const val MAX_HEAT = 100f
        const val FIXED_STEP = 1f / 120f
        const val DASH_INPUT_BUFFER_SECONDS = 0.16f
        const val MAX_PROJECTILES = 650
        const val MAX_PICKUPS = 420
        const val MAX_TRAIL_POINTS = 110
        const val MAX_DELAYED_RELIC_HITS = 256
        const val MAX_GAMEPLAY_SOUND_CUES = 32
        const val MAX_WEAPON_NODES = 8
        const val MAX_WEAPON_ORBITALS = 8
        const val MAX_CHOICES = 4
        const val MAX_TRAIL_SAMPLES_PER_UPDATE = 32
    }

    internal var gameplayRandom: CloneableXorWowRandom = reductionSource?.gameplayRandom?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    } ?: CloneableXorWowRandom(seed)
    internal var activeRebirthProfile: RebirthProfile = reductionSource?.activeRebirthProfile
        ?: content.rebirth.profile(
            bootstrapProgress?.rebirthProgress?.level ?: initialRebirthLevel,
        )
    internal val unlockedWeaponSet: CopyOnWriteMutableSet<WeaponId> = reductionSource?.unlockedWeaponSet?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteMutableSet(mutableSetOf<WeaponId>().apply {
            addAll(bootstrapProgress?.loadout?.unlockedWeapons.orEmpty())
            add(WeaponId.FLUX_WAKE)
        })
    internal var unlockedWeaponView: Set<WeaponId> = reductionSource?.unlockedWeaponView
        ?: unlockedWeaponSet.toSet()
    internal val metaRanks: CopyOnWriteIntArray = reductionSource?.metaRanks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.metaUpgrades.size) { index ->
            val definition = content.metaUpgrades[index]
            bootstrapProgress?.labProgress?.ranks?.getOrNull(index)
                ?.coerceIn(0, definition.maxRanks) ?: 0
        })
    internal val discoveredItemIds: CopyOnWriteMutableSet<Int> = reductionSource?.discoveredItemIds?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteMutableSet(
            bootstrapProgress?.collection?.discoveredItemIds
                ?.filterTo(mutableSetOf()) { content.item(it) != null }
                ?: mutableSetOf(),
        )
    internal var pendingDiscoveredItemIdStorage: CopyOnWriteMutableSet<Int>? =
        reductionSource?.pendingDiscoveredItemIdStorage?.fork()
    internal val pendingDiscoveredItemIds: PendingDiscoveredItemIdBuffer
        get() = PendingDiscoveredItemIdBuffer(this)
    internal val itemStacks: CopyOnWriteIntArray = reductionSource?.itemStacks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.items.size))
    internal val familyStacks: CopyOnWriteIntArray = reductionSource?.familyStacks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.items.maxOfOrNull { it.id / 20 + 1 } ?: 0))
    internal var soundCueStorage: MutableList<GameplayAudioCue>? =
        reductionSource?.soundCueStorage?.let { source -> ArrayList(source) }
    internal val soundCues: PendingSoundCueBuffer
        get() = PendingSoundCueBuffer(this)
    internal var visualFxCueStorage: BoundedVisualFxCueAccumulator? =
        reductionSource?.visualFxCueStorage?.copy()
    internal val visualFxCues: PendingVisualFxCueBuffer
        get() = PendingVisualFxCueBuffer(this)
    internal var pendingBankedMatter: Long = reductionSource?.pendingBankedMatter ?: 0L
    internal var pendingClearedRebirthLevel: Int? = reductionSource?.pendingClearedRebirthLevel
    internal var characterRuntime: CharacterRuntime = reductionSource?.characterRuntime ?: CharacterRuntime()
    internal var pendingEliteKills: Int = reductionSource?.pendingEliteKills ?: 0
    internal var pendingDashHits: Int = reductionSource?.pendingDashHits ?: 0
    internal var pendingCompletedOrbits: Int = reductionSource?.pendingCompletedOrbits ?: 0
    internal var pendingArchitectDefeatedWith: CoreShape? = reductionSource?.pendingArchitectDefeatedWith
    internal var pointsOfInterest: List<PointOfInterestState> = reductionSource?.pointsOfInterest ?: emptyList()
    internal var nextPointOfferIndex: Int = reductionSource?.nextPointOfferIndex ?: 0
    internal var pendingDirectedRewards: List<kinetickk.ball.content.api.DirectedReward> = reductionSource?.pendingDirectedRewards ?: emptyList()
    internal var directedReward: kinetickk.ball.content.api.DirectedReward? = reductionSource?.directedReward
    internal var selectedRewardFocus: kinetickk.ball.content.api.RewardFocus? = reductionSource?.selectedRewardFocus

    internal var nextEntityId: Int = reductionSource?.nextEntityId ?: 1
    internal var spawnClock: Float = reductionSource?.spawnClock ?: 0f
    internal var nextEliteAt: Float = reductionSource?.nextEliteAt ?: content.tempo.firstEliteAtSeconds
    internal var dashBufferTime: Float = reductionSource?.dashBufferTime ?: 0f
    internal var bossSpawned: Boolean = reductionSource?.bossSpawned ?: false
    internal var keyboardBrakeActive: Boolean = reductionSource?.keyboardBrakeActive ?: false
    internal var secondaryBrakeActive: Boolean = reductionSource?.secondaryBrakeActive ?: false
    internal var touchBrakeActive: Boolean = reductionSource?.touchBrakeActive ?: false
    internal var uiScale: Float = reductionSource?.uiScale ?: 1f
    internal var accumulator: Float = reductionSource?.accumulator ?: 0f
    internal var lastTransitionSteps: Int = reductionSource?.lastTransitionSteps ?: 0
        internal set
    internal var previousCoreX: Float = reductionSource?.previousCoreX ?: 0f
    internal var previousCoreY: Float = reductionSource?.previousCoreY ?: 0f
    internal var previousSingularityX: Float = reductionSource?.previousSingularityX ?: 0f
    internal var previousSingularityY: Float = reductionSource?.previousSingularityY ?: 0f
    internal var trailLastX: Float = reductionSource?.trailLastX ?: 0f
    internal var trailLastY: Float = reductionSource?.trailLastY ?: 0f
    internal var trailDistanceCarry: Float = reductionSource?.trailDistanceCarry ?: 0f
    internal var weaponClock: Float = reductionSource?.weaponClock ?: 0f
    internal var weaponSecondaryClock: Float = reductionSource?.weaponSecondaryClock ?: 0f
    internal var pendingLevelChoices: Int = reductionSource?.pendingLevelChoices ?: 0
    internal var pendingRelicChoices: Int = reductionSource?.pendingRelicChoices ?: 0
    internal var pendingBindingRelic: RelicId? = reductionSource?.pendingBindingRelic
    internal var pendingRelicBindAction: RelicChoiceAction? = reductionSource?.pendingRelicBindAction
    internal val relicRanks: CopyOnWriteIntArray = reductionSource?.relicRanks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.relics.size))
    internal val relicCooldowns: CopyOnWriteFloatArray = reductionSource?.relicCooldowns?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteFloatArray(FloatArray(content.relics.size))
    internal val relicCounters: CopyOnWriteIntArray = reductionSource?.relicCounters?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.relics.size))
    internal val relicProcCounts: CopyOnWriteIntArray = reductionSource?.relicProcCounts?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.relics.size))
    internal val delayedRelicHits: MutableList<DelayedRelicHit> = reductionSource?.delayedRelicHits
        ?.let { source ->
            if (shareStableReductionStorage) {
                source
            } else {
                source.mapTo(ArrayList(source.size), DelayedRelicHit::copy)
            }
        }
        ?: mutableListOf()
    internal val agonyMutationCounts: CopyOnWriteIntArray = reductionSource?.agonyMutationCounts?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.weapons.size))
    internal var slipstreamRelayTime: Float = reductionSource?.slipstreamRelayTime ?: 0f
    internal var borrowedMomentTime: Float = reductionSource?.borrowedMomentTime ?: 0f
    internal var brakepointCharge: Float = reductionSource?.brakepointCharge ?: 0f
    internal var synergyEffects: List<SynergyEffect> = reductionSource?.synergyEffects ?: emptyList()
    internal val synergyCooldowns: CopyOnWriteFloatArray = reductionSource?.synergyCooldowns?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CopyOnWriteFloatArray(FloatArray(kinetickk.ball.content.api.SynergyId.entries.size))
    internal var synergyManeuverCharge: Float = reductionSource?.synergyManeuverCharge ?: 0f
    internal var ghostDashPending: Boolean = reductionSource?.ghostDashPending ?: false
    internal var ghostDashStartX: Float = reductionSource?.ghostDashStartX ?: 0f
    internal var ghostDashStartY: Float = reductionSource?.ghostDashStartY ?: 0f
    internal var dataFraction: Float = reductionSource?.dataFraction ?: 0f
    internal var matterFraction: Float = reductionSource?.matterFraction ?: 0f
    internal var shieldRechargeDelay: Float = reductionSource?.shieldRechargeDelay ?: 0f
    internal var overheatHoldTime: Float = reductionSource?.overheatHoldTime ?: 0f
    internal var saturationHeadingX: Float = reductionSource?.saturationHeadingX ?: 1f
    internal var saturationHeadingY: Float = reductionSource?.saturationHeadingY ?: 0f
    internal var smoothedVelocityX: Float = reductionSource?.smoothedVelocityX ?: 0f
    internal var smoothedVelocityY: Float = reductionSource?.smoothedVelocityY ?: 0f
    internal var turnHeadingEstablished: Boolean = reductionSource?.turnHeadingEstablished ?: false
    internal var turnHoldTime: Float = reductionSource?.turnHoldTime ?: 0f
    internal var turnRecoveryCooldown: Float = reductionSource?.turnRecoveryCooldown ?: 0f
    internal var turnDirection: Int = reductionSource?.turnDirection ?: 0
    internal var timeSinceDamage: Float = reductionSource?.timeSinceDamage ?: 0f
    internal var hurtCooldown: Float = reductionSource?.hurtCooldown ?: 0f
    internal var lastAimDirectionX: Float = reductionSource?.lastAimDirectionX ?: 1f
    internal var lastAimDirectionY: Float = reductionSource?.lastAimDirectionY ?: 0f
    internal var bankedThisRun: Boolean = reductionSource?.bankedThisRun ?: false
    internal var activeChoiceType: ChoiceType = reductionSource?.activeChoiceType ?: ChoiceType.ITEM

    var phase: GamePhase = reductionSource?.phase ?: GamePhase.RUNNING
        internal set
    var settings: PlayerPreferences = reductionSource?.settings ?: bootstrapProgress?.preferences ?: PlayerPreferences()
        internal set
    var rebirthLevel: Int = reductionSource?.rebirthLevel ?: activeRebirthProfile.tier
        internal set
    var screenWidth: Float = reductionSource?.screenWidth ?: 1280f
        internal set
    var screenHeight: Float = reductionSource?.screenHeight ?: 720f
        internal set

    var coreX: Float = reductionSource?.coreX ?: 0f
        internal set
    var coreY: Float = reductionSource?.coreY ?: 0f
        internal set
    var velocityX: Float = reductionSource?.velocityX ?: 0f
        internal set
    var velocityY: Float = reductionSource?.velocityY ?: 0f
        internal set
    var cameraX: Float = reductionSource?.cameraX ?: 0f
        internal set
    var cameraY: Float = reductionSource?.cameraY ?: 0f
        internal set
    var pointerX: Float = reductionSource?.pointerX ?: 900f
        internal set
    var pointerY: Float = reductionSource?.pointerY ?: 360f
        internal set
    var pointerActive: Boolean = reductionSource?.pointerActive ?: true
        internal set
    var braking: Boolean = reductionSource?.braking ?: false
        internal set

    var elapsed: Float = reductionSource?.elapsed ?: 0f
        internal set
    var heat: Float = reductionSource?.heat ?: 0f
        internal set
    var overheated: Boolean = reductionSource?.overheated ?: false
        internal set
    var dashPhaseTime: Float = reductionSource?.dashPhaseTime ?: 0f
        internal set
    var hp: Float = reductionSource?.hp ?: 100f
        internal set
    var maxHp: Float = reductionSource?.maxHp ?: 100f
        internal set
    var shield: Float = reductionSource?.shield ?: 0f
        internal set
    var maxShield: Float = reductionSource?.maxShield ?: 0f
        internal set
    var level: Int = reductionSource?.level ?: 1
        internal set
    var data: Int = reductionSource?.data ?: 0
        internal set
    var nextLevelData: Int = reductionSource?.nextLevelData ?: content.tempo.dataRequiredForLevel(1)
        internal set
    var keys: Int = reductionSource?.keys ?: 0
        internal set
    var kills: Int = reductionSource?.kills ?: 0
        internal set
    var combo: Int = reductionSource?.combo ?: 0
        internal set
    var comboTime: Float = reductionSource?.comboTime ?: 0f
        internal set
    var runMatter: Long = reductionSource?.runMatter ?: 0L
        internal set
    var totalMatter: Long = reductionSource?.totalMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.matter ?: 0L
        internal set
    var lifetimeMatter: Long = reductionSource?.lifetimeMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.lifetimeMatter ?: totalMatter
        internal set
    var lastImpact: Float = reductionSource?.lastImpact ?: 0f
        internal set
    var lastImpactTime: Float = reductionSource?.lastImpactTime ?: 0f
        internal set
    var damageFlash: Float = reductionSource?.damageFlash ?: 0f
        internal set
    var runGrace: Float = reductionSource?.runGrace ?: 0f
        internal set
    var screenShake: Float = reductionSource?.screenShake ?: 0f
        internal set
    var message: String = reductionSource?.message ?: ""
        internal set
    var messageTime: Float = reductionSource?.messageTime ?: 0f
        internal set

    var mass: Float = reductionSource?.mass ?: 1f
        internal set
    var damageMultiplier: Float = reductionSource?.damageMultiplier ?: 1f
        internal set
    var weaponPower: Float = reductionSource?.weaponPower ?: 1f
        internal set
    var coolingRate: Float = reductionSource?.coolingRate ?: 19f
        internal set
    var magnetStrength: Float = reductionSource?.magnetStrength ?: 4.65f
        internal set
    var dashImpulse: Float = reductionSource?.dashImpulse ?: 590f
        internal set
    var dashHeatCost: Float = reductionSource?.dashHeatCost ?: 36f
        internal set
    var regenPerSecond: Float = reductionSource?.regenPerSecond ?: 0f
        internal set
    var critChance: Float = reductionSource?.critChance ?: 0.05f
        internal set
    var critMultiplier: Float = reductionSource?.critMultiplier ?: 1.5f
        internal set
    var pickupRadius: Float = reductionSource?.pickupRadius ?: 150f
        internal set
    var luck: Float = reductionSource?.luck ?: 0f
        internal set
    var dataGain: Float = reductionSource?.dataGain ?: 1f
        internal set
    var matterGain: Float = reductionSource?.matterGain ?: 1f
        internal set
    var attackSpeed: Float = reductionSource?.attackSpeed ?: 1f
        internal set
    var damageReduction: Float = reductionSource?.damageReduction ?: 0f
        internal set
    var comboWindow: Float = reductionSource?.comboWindow ?: 2.8f
        internal set
    var overdriveGain: Float = reductionSource?.overdriveGain ?: 1f
        internal set
    var dragCoefficient: Float = reductionSource?.dragCoefficient ?: 0.29f
        internal set
    var polarityStability: Float = reductionSource?.polarityStability ?: 1f
        internal set

    var weapon: WeaponId = reductionSource?.weapon
        ?: bootstrapProgress?.loadout?.selectedWeapon ?: WeaponId.FLUX_WAKE
        internal set
    var startingWeapon: WeaponId = reductionSource?.startingWeapon ?: weapon
        internal set
    var weaponLevel: Int = reductionSource?.weaponLevel ?: 1
        internal set
    var overdriveCharge: Float = reductionSource?.overdriveCharge ?: 0f
        internal set
    var overdriveTime: Float = reductionSource?.overdriveTime ?: 0f
        internal set
    var rerollsRemaining: Int = reductionSource?.rerollsRemaining ?: 1
        internal set
    var acquiredItemCount: Int = reductionSource?.acquiredItemCount ?: 0
        internal set
    var recentItem: ItemDefinition? = reductionSource?.recentItem
        internal set
    var equippedRelics: List<EquippedRelic> = reductionSource?.equippedRelics ?: emptyList()
        internal set

    var morningstarAngle: Float = reductionSource?.morningstarAngle ?: 0f
        internal set
    var morningstarX: Float = reductionSource?.morningstarX ?: 0f
        internal set
    var morningstarY: Float = reductionSource?.morningstarY ?: 0f
        internal set
    var weaponBeamTime: Float = reductionSource?.weaponBeamTime ?: 0f
        internal set
    var weaponBeamStartX: Float = reductionSource?.weaponBeamStartX ?: 0f
        internal set
    var weaponBeamStartY: Float = reductionSource?.weaponBeamStartY ?: 0f
        internal set
    var weaponBeamEndX: Float = reductionSource?.weaponBeamEndX ?: 0f
        internal set
    var weaponBeamEndY: Float = reductionSource?.weaponBeamEndY ?: 0f
        internal set

    var totem: Totem? = reductionSource?.totem?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    }
        internal set
    var coreShape: CoreShape = reductionSource?.coreShape
        ?: bootstrapProgress?.loadout?.coreShape ?: CoreShape.ORB
        internal set

    val enemies: MutableList<Enemy> = reductionSource?.enemies?.let { source ->
        if (shareStableReductionStorage) {
            source
        } else {
            source.mapTo(ArrayList(source.size), Enemy::isolatedCopy)
        }
    } ?: mutableListOf()
    val projectiles: MutableList<Projectile> = reductionSource?.projectiles?.let { source ->
        if (shareStableReductionStorage) source
        else source.mapTo(ArrayList(source.size), Projectile::isolatedCopy)
    }
        ?: mutableListOf()
    val pickups: MutableList<Pickup> = reductionSource?.pickups?.let { source ->
        if (shareStableReductionStorage) source
        else source.mapTo(ArrayList(source.size), Pickup::copy)
    }
        ?: mutableListOf()
    val trail: MutableList<TrailPoint> = reductionSource?.trail?.let { source ->
        if (shareStableReductionStorage) source
        else source.mapTo(ArrayList(source.size), TrailPoint::copy)
    }
        ?: mutableListOf()
    val weaponNodes: MutableList<WeaponNode> = reductionSource?.weaponNodes?.let { source ->
        if (shareStableReductionStorage) source
        else source.mapTo(ArrayList(source.size), WeaponNode::copy)
    }
        ?: mutableListOf()
    val weaponOrbitals: MutableList<WeaponOrbital> = reductionSource?.weaponOrbitals?.let { source ->
        if (shareStableReductionStorage) source
        else source.mapTo(ArrayList(source.size), WeaponOrbital::copy)
    }
        ?: mutableListOf()
    var choices: List<ChoiceOption> = reductionSource?.choices ?: emptyList()
        internal set(value) {
            require(value.size <= MAX_CHOICES) {
                "choices cannot exceed $MAX_CHOICES entries"
            }
            field = value
        }

    val speed: Float get() = length(velocityX, velocityY)
    internal val threatElapsed: Float get() = elapsed + rebirthProfile.threatTimeOffsetSeconds
    val rebirthProfile: RebirthProfile get() = activeRebirthProfile
    val runProgress: Float get() = content.tempo.progress(elapsed)
    val tetherDistance: Float
        get() {
            val tx = cameraX + pointerX - screenWidth * 0.5f
            val ty = cameraY + pointerY - screenHeight * 0.5f
            return length(tx - coreX, ty - coreY)
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
    val unlockedWeapons: Set<WeaponId> get() = unlockedWeaponView
    val currentWeaponDefinition: WeaponDefinition get() = content.weapon(weapon)
    val currentWeaponMastery: WeaponMastery get() = content.weaponMasteryForLevel(weaponLevel)
    val nextWeaponMastery: WeaponMastery? get() = content.weaponMasteryAfter(weaponLevel)
    val weaponMasteryProgress: Float
        get() {
            val current = currentWeaponMastery
            val next = nextWeaponMastery ?: return 1f
            return clamp(
                (weaponLevel - current.minimumLevel).toFloat() / (next.minimumLevel - current.minimumLevel),
                0f,
                1f,
            )
        }
    val choiceType: ChoiceType get() = activeChoiceType
    val choicesCanReroll: Boolean
        get() = phase == GamePhase.CHOICE && directedReward == null && rerollsRemaining > 0 && when (activeChoiceType) {
            ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> true
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> false
        }
    val pendingRelicChoiceCount: Int get() = pendingRelicChoices

    fun relicRank(id: RelicId): Int = relicRanks[id.ordinal]

    init {
        if (reductionSource == null && startingWeapon !in unlockedWeaponSet) {
            startingWeapon = WeaponId.FLUX_WAKE
            weapon = startingWeapon
        }
    }

    fun resize(width: Float, height: Float, density: Float = 1f) {
        val newWidth = max(1f, width)
        val newHeight = max(1f, height)
        val dimensionsChanged = newWidth != screenWidth || newHeight != screenHeight
        if (dimensionsChanged) {
            pointerX = (pointerX / screenWidth * newWidth).coerceIn(0f, newWidth)
            pointerY = (pointerY / screenHeight * newHeight).coerceIn(0f, newHeight)
            screenWidth = newWidth
            screenHeight = newHeight
            previousSingularityX = cameraX + pointerX - screenWidth * 0.5f
            previousSingularityY = cameraY + pointerY - screenHeight * 0.5f
        }
        uiScale = max(1f, density)
    }

}

/** Produces an isolated candidate; committed instances are never mutated by a reduction. */
internal fun MutableGameState.copyForReduction(): MutableGameState =
    MutableGameState(content = content, reductionSource = this)

/** Only for inputs whose reduction does not mutate stable simulation collections or randomness. */
internal fun MutableGameState.copyForScalarInputReduction(): MutableGameState =
    MutableGameState(
        content = content,
        reductionSource = this,
        shareStableReductionStorage = true,
    )

/**
 * Allocation-free owner view over discoveries awaiting publication.
 *
 * The actual COW set is absent for the overwhelmingly common empty state. A reduction fork only
 * creates its small COW facade when there is retained output to preserve.
 */
@JvmInline
internal value class PendingDiscoveredItemIdBuffer(
    private val owner: MutableGameState,
) {
    val size: Int
        get() = owner.pendingDiscoveredItemIdStorage?.size ?: 0

    fun isEmpty(): Boolean = owner.pendingDiscoveredItemIdStorage?.isEmpty() != false

    fun isNotEmpty(): Boolean = owner.pendingDiscoveredItemIdStorage?.isNotEmpty() == true

    operator fun contains(itemId: Int): Boolean =
        owner.pendingDiscoveredItemIdStorage?.contains(itemId) == true

    fun add(itemId: Int): Boolean {
        owner.pendingDiscoveredItemIdStorage?.let { return it.add(itemId) }
        owner.pendingDiscoveredItemIdStorage = CopyOnWriteMutableSet(mutableSetOf(itemId))
        return true
    }

    operator fun plusAssign(itemId: Int) {
        add(itemId)
    }

    fun clear() {
        owner.pendingDiscoveredItemIdStorage = null
    }

    fun toList(): List<Int> = owner.pendingDiscoveredItemIdStorage?.toList().orEmpty()

    fun toImmutableSet(): ImmutableSet<Int> =
        owner.pendingDiscoveredItemIdStorage?.toImmutableSet() ?: immutableSetOf()

    operator fun iterator(): Iterator<Int> =
        owner.pendingDiscoveredItemIdStorage?.iterator() ?: emptySet<Int>().iterator()
}

/** Allocation-free owner view over the lazily materialized bounded audio batch. */
@JvmInline
internal value class PendingSoundCueBuffer(
    private val owner: MutableGameState,
) {
    val size: Int
        get() = owner.soundCueStorage?.size ?: 0

    fun isEmpty(): Boolean = owner.soundCueStorage.isNullOrEmpty()

    fun isNotEmpty(): Boolean = !owner.soundCueStorage.isNullOrEmpty()

    fun add(cue: GameplayAudioCue): Boolean {
        val retained = owner.soundCueStorage
        if (retained != null) return retained.add(cue)
        owner.soundCueStorage = arrayListOf(cue)
        return true
    }

    operator fun plusAssign(cue: GameplayAudioCue) {
        add(cue)
    }

    fun clear() {
        owner.soundCueStorage = null
    }

    fun toList(): List<GameplayAudioCue> = owner.soundCueStorage?.toList().orEmpty()

    fun toImmutableList(): ImmutableList<GameplayAudioCue> =
        owner.soundCueStorage?.toImmutableList() ?: immutableListOf()

    operator fun iterator(): Iterator<GameplayAudioCue> =
        owner.soundCueStorage?.iterator() ?: emptyList<GameplayAudioCue>().iterator()
}

/** Allocation-free owner view over the lazily materialized visual batch. */
@JvmInline
internal value class PendingVisualFxCueBuffer(
    private val owner: MutableGameState,
) {
    fun isEmpty(): Boolean = owner.visualFxCueStorage?.isEmpty() != false

    fun record(cue: VisualFxCue) {
        val accumulator = owner.visualFxCueStorage ?: BoundedVisualFxCueAccumulator().also {
            owner.visualFxCueStorage = it
        }
        accumulator.record(cue)
    }

    fun drain(): ImmutableList<VisualFxCue> {
        val accumulator = owner.visualFxCueStorage ?: return immutableListOf()
        owner.visualFxCueStorage = null
        return accumulator.drain()
    }
}
