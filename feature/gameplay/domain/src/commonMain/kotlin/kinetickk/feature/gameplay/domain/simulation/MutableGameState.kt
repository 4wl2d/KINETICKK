// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.gameplay.domain.simulation

import kinetickk.core.content.*

import kinetickk.core.random.CloneableXorWowRandom
import kinetickk.core.profile.api.GameplayProfileSnapshot
import kinetickk.core.profile.api.PlayerPreferences
import kinetickk.feature.gameplay.domain.model.*
import kinetickk.feature.gameplay.domain.protocol.BoundedVisualFxCueAccumulator
import kinetickk.core.audio.api.AudioCue
import kotlin.math.max
import kotlin.math.min


internal class MutableGameState(
    seed: Int = 731_991,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
    bootstrapProgress: GameplayProfileSnapshot? = null,
) {
    companion object {
        const val RUN_DURATION_SECONDS = 20f * 60f
        const val CURSOR_KILL_RADIUS = 22f
        const val CORE_RADIUS = 16f
        const val MAX_HEAT = 100f
        const val FIXED_STEP = 1f / 120f
        const val DASH_INPUT_BUFFER_SECONDS = 0.16f
        const val MAX_ENEMIES = RebirthProgression.MAX_ACTIVE_ENEMIES
        const val MAX_PROJECTILES = 650
        const val MAX_PICKUPS = 420
        const val MAX_TRAIL_POINTS = 110
        const val MAX_DELAYED_RELIC_HITS = 256

        val SIMULATION_SPEEDS = listOf(0.75f, 1f, 1.15f, 1.35f, 1.6f, 2f)
    }

    internal var gameplayRandom = CloneableXorWowRandom(seed)
    internal var activeRebirthProfile = RebirthProgression.profile(
        bootstrapProgress?.rebirthProgress?.level ?: initialRebirthLevel,
    )
    internal val unlockedWeaponSet = mutableSetOf<WeaponId>().apply {
        addAll(bootstrapProgress?.loadout?.unlockedWeapons.orEmpty())
        add(WeaponId.FLUX_WAKE)
    }
    internal var unlockedWeaponView: Set<WeaponId> = unlockedWeaponSet.toSet()
    internal val metaRanks = IntArray(MetaUpgradeId.entries.size) { index ->
        val definition = MetaUpgradeCatalog.all[index]
        bootstrapProgress?.labProgress?.ranks?.getOrNull(index)?.coerceIn(0, definition.maxRanks) ?: 0
    }
    internal val discoveredItemIds = bootstrapProgress?.collection?.discoveredItemIds
        ?.filterTo(mutableSetOf()) { it in 0 until ItemCatalog.ITEM_COUNT }
        ?: mutableSetOf()
    internal val pendingDiscoveredItemIds = mutableSetOf<Int>()
    internal val itemStacks = IntArray(ItemCatalog.ITEM_COUNT)
    internal val familyStacks = IntArray(20)
    internal val soundCues = mutableListOf<AudioCue>()
    internal var visualFxCues = BoundedVisualFxCueAccumulator()
    internal var pendingBankedMatter = 0L
    internal var pendingClearedRebirthLevel: Int? = null

    internal var nextEntityId = 1
    internal var spawnClock = 0f
    internal var nextEliteAt = 38f
    internal var dashBufferTime = 0f
    internal var bossSpawned = false
    internal var keyboardBrakeActive = false
    internal var secondaryBrakeActive = false
    internal var touchBrakeActive = false
    internal var uiScale = 1f
    internal var accumulator = 0f
    internal var lastTransitionSteps = 0
        internal set
    internal var previousCoreX = 0f
    internal var previousCoreY = 0f
    internal var previousSingularityX = 0f
    internal var previousSingularityY = 0f
    internal var trailLastX = 0f
    internal var trailLastY = 0f
    internal var trailDistanceCarry = 0f
    internal var weaponClock = 0f
    internal var weaponSecondaryClock = 0f
    internal var pendingLevelChoices = 0
    internal var pendingRelicChoices = 0
    internal var pendingBindingRelic: RelicId? = null
    internal var pendingRelicBindAction: RelicChoiceAction? = null
    internal val relicRanks = IntArray(RelicCatalog.RELIC_COUNT)
    internal val relicCooldowns = FloatArray(RelicCatalog.RELIC_COUNT)
    internal val relicCounters = IntArray(RelicCatalog.RELIC_COUNT)
    internal val relicProcCounts = IntArray(RelicCatalog.RELIC_COUNT)
    internal val delayedRelicHits = mutableListOf<DelayedRelicHit>()
    internal val agonyMutationCounts = IntArray(WeaponId.entries.size)
    internal var slipstreamRelayTime = 0f
    internal var borrowedMomentTime = 0f
    internal var brakepointCharge = 0f
    internal var dataFraction = 0f
    internal var matterFraction = 0f
    internal var shieldRechargeDelay = 0f
    internal var overheatHoldTime = 0f
    internal var saturationHeadingX = 1f
    internal var saturationHeadingY = 0f
    internal var timeSinceDamage = 0f
    internal var hurtCooldown = 0f
    internal var lastAimDirectionX = 1f
    internal var lastAimDirectionY = 0f
    internal var bankedThisRun = false
    internal var activeChoiceType = ChoiceType.ITEM

    var phase = GamePhase.RUNNING
        internal set
    var settings = bootstrapProgress?.preferences ?: PlayerPreferences()
        internal set
    var rebirthLevel = activeRebirthProfile.tier
        internal set
    var screenWidth = 1280f
        internal set
    var screenHeight = 720f
        internal set

    var coreX = 0f
        internal set
    var coreY = 0f
        internal set
    var velocityX = 0f
        internal set
    var velocityY = 0f
        internal set
    var cameraX = 0f
        internal set
    var cameraY = 0f
        internal set
    var pointerX = 900f
        internal set
    var pointerY = 360f
        internal set
    var pointerActive = true
        internal set
    var braking = false
        internal set

    var elapsed = 0f
        internal set
    var heat = 0f
        internal set
    var overheated = false
        internal set
    var dashPhaseTime = 0f
        internal set
    var hp = 100f
        internal set
    var maxHp = 100f
        internal set
    var shield = 0f
        internal set
    var maxShield = 0f
        internal set
    var level = 1
        internal set
    var data = 0
        internal set
    var nextLevelData = 18
        internal set
    var keys = 0
        internal set
    var kills = 0
        internal set
    var combo = 0
        internal set
    var comboTime = 0f
        internal set
    var runMatter = 0L
        internal set
    var totalMatter = initialMatter?.toLong() ?: bootstrapProgress?.economy?.matter ?: 0L
        internal set
    var lifetimeMatter = initialMatter?.toLong() ?: bootstrapProgress?.economy?.lifetimeMatter ?: totalMatter
        internal set
    var lastImpact = 0f
        internal set
    var lastImpactTime = 0f
        internal set
    var damageFlash = 0f
        internal set
    var runGrace = 0f
        internal set
    var screenShake = 0f
        internal set
    var message = ""
        internal set
    var messageTime = 0f
        internal set

    var mass = 1f
        internal set
    var damageMultiplier = 1f
        internal set
    var weaponPower = 1f
        internal set
    var coolingRate = 19f
        internal set
    var magnetStrength = 4.65f
        internal set
    var dashImpulse = 590f
        internal set
    var dashHeatCost = 36f
        internal set
    var regenPerSecond = 0f
        internal set
    var critChance = 0.05f
        internal set
    var critMultiplier = 1.5f
        internal set
    var pickupRadius = 150f
        internal set
    var luck = 0f
        internal set
    var dataGain = 1f
        internal set
    var matterGain = 1f
        internal set
    var attackSpeed = 1f
        internal set
    var damageReduction = 0f
        internal set
    var comboWindow = 2.8f
        internal set
    var overdriveGain = 1f
        internal set
    var dragCoefficient = 0.29f
        internal set
    var polarityStability = 1f
        internal set

    var weapon = bootstrapProgress?.loadout?.selectedWeapon ?: WeaponId.FLUX_WAKE
        internal set
    var startingWeapon = weapon
        internal set
    var weaponLevel = 1
        internal set
    var overdriveCharge = 0f
        internal set
    var overdriveTime = 0f
        internal set
    var rerollsRemaining = 1
        internal set
    var acquiredItemCount = 0
        internal set
    var recentItem: ItemDefinition? = null
        internal set
    var equippedRelics: List<EquippedRelic> = emptyList()
        internal set

    var morningstarAngle = 0f
        internal set
    var morningstarX = 0f
        internal set
    var morningstarY = 0f
        internal set
    var weaponBeamTime = 0f
        internal set
    var weaponBeamStartX = 0f
        internal set
    var weaponBeamStartY = 0f
        internal set
    var weaponBeamEndX = 0f
        internal set
    var weaponBeamEndY = 0f
        internal set

    var totem: Totem? = null
        internal set
    var coreShape: CoreShape = CoreShape.ORB
        internal set

    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<Pickup>()
    val trail = mutableListOf<TrailPoint>()
    val weaponNodes = mutableListOf<WeaponNode>()
    val weaponOrbitals = mutableListOf<WeaponOrbital>()
    var choices: List<ChoiceOption> = emptyList()
        internal set

    val speed: Float get() = length(velocityX, velocityY)
    internal val threatElapsed: Float get() = elapsed + rebirthProfile.threatTimeOffsetSeconds
    val rebirthProfile: RebirthProfile get() = activeRebirthProfile
    val runProgress: Float get() = clamp(elapsed / RUN_DURATION_SECONDS, 0f, 1f)
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
    val currentWeaponDefinition: WeaponDefinition get() = WeaponCatalog.byId(weapon)
    val currentWeaponMastery: WeaponMastery get() = WeaponMastery.forLevel(weaponLevel)
    val nextWeaponMastery: WeaponMastery? get() = WeaponMastery.after(weaponLevel)
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
        get() = phase == GamePhase.CHOICE && rerollsRemaining > 0 && when (activeChoiceType) {
            ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> true
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> false
        }
    val pendingRelicChoiceCount: Int get() = pendingRelicChoices

    fun relicRank(id: RelicId): Int = relicRanks[id.ordinal]

    init {
        if (startingWeapon !in unlockedWeaponSet) {
            startingWeapon = WeaponId.FLUX_WAKE
            weapon = startingWeapon
        }
        coreShape = bootstrapProgress?.loadout?.coreShape ?: CoreShape.ORB
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
