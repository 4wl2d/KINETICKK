// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.features.game.nucleus

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kinetickk.features.game.nucleus.protocol.SoundCue
import kinetickk.features.game.nucleus.protocol.BoundedVisualFxCueAccumulator
import kinetickk.features.game.nucleus.protocol.GameCommand
import kinetickk.features.game.nucleus.protocol.GameProfileReplica
import kinetickk.features.game.nucleus.protocol.ProfileChange
import kinetickk.features.game.nucleus.protocol.SettingsChange
import kinetickk.features.game.nucleus.projection.EnemyProjection
import kinetickk.features.game.nucleus.projection.GameProjection
import kinetickk.features.game.nucleus.projection.PickupProjection
import kinetickk.features.game.nucleus.projection.ProjectileProjection
import kinetickk.features.game.nucleus.projection.TotemProjection
import kinetickk.features.game.nucleus.projection.TrailPointProjection
import kinetickk.features.game.nucleus.projection.WeaponNodeProjection
import kinetickk.features.game.nucleus.projection.WeaponOrbitalProjection
import kinetickk.features.game.nucleus.protocol.VisualFxCue
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.random.CloneableXorWowRandom

enum class GamePhase { MENU, RUNNING, PAUSED, CHOICE, GAME_OVER, VICTORY }
enum class UiScreen { GAME, SETTINGS, LAB, ARMORY, REBIRTH, CODEX }
enum class SettingsRow {
    SFX,
    MUSIC,
    MASTER_VOLUME,
    SIMULATION_SPEED,
    TEXT_SIZE,
    SCREEN_SHAKE,
    PARTICLES,
    DAMAGE_NUMBERS,
    DAMAGE_NUMBER_SIZE,
    DAMAGE_NUMBER_FORMAT,
    DAMAGE_COLOR_THRESHOLDS,
}

private const val SETTINGS_MIN_ROW_SPACING_DP = 32f

internal fun settingsRowsPerPage(availableHeight: Float, density: Float): Int {
    val logicalHeight = availableHeight.coerceAtLeast(0f) / density.coerceAtLeast(1f)
    return floor(logicalHeight / SETTINGS_MIN_ROW_SPACING_DP)
        .toInt()
        .coerceIn(1, SettingsRow.entries.size)
}
enum class EnemyType {
    DRIFTER,
    SHOOTER,
    CHARGER,
    INTERCEPTOR,
    WEAVER,
    WARDEN,
    SPLITTER,
    ELITE,
    ARCHITECT,
}
enum class PickupType { DATA, KEY, REPAIR, RELIC }
enum class CoreShape { ORB, PRISM, SHARD }
enum class ChoiceType { ITEM, TOTEM, WEAPON, RELIC, RELIC_BIND }
enum class TotemAction { AMPLIFY_CURRENT, CHANGE_WEAPON }
enum class RelicChoiceAction { ACQUIRE, MELD, REPLACE, MELD_TARGET }
enum class WeaponNodeType { GRAVITY_MINE }

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
)

data class Enemy(
    val id: Int,
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var hp: Float,
    val maxHp: Float,
    val radius: Float,
    var actionTimer: Float = 0f,
    var flash: Float = 0f,
    var contactCooldown: Float = 0f,
    var weaponCooldown: Float = 0f,
    var previousX: Float = x,
    var previousY: Float = y,
    var dead: Boolean = false,
    var relicKillProcsEligible: Boolean = false,
    var relicQualificationCooldown: Float = 0f,
    val relicCounters: IntArray = IntArray(RelicCatalog.RELIC_COUNT),
    val relicTimers: FloatArray = FloatArray(RelicCatalog.RELIC_COUNT),
    val relicValues: FloatArray = FloatArray(RelicCatalog.RELIC_COUNT),
)

data class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    var life: Float,
    val hostile: Boolean = true,
    val damage: Float = 0f,
    var pierce: Int = 0,
    val colorIndex: Int = 0,
    val sourceWeapon: WeaponId? = null,
    var previousX: Float = x,
    var previousY: Float = y,
    val hitEnemyIds: MutableSet<Int> = mutableSetOf(),
)

data class Pickup(
    val type: PickupType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var life: Float = 20f,
    var previousX: Float = x,
    var previousY: Float = y,
)

data class TrailPoint(var x: Float, var y: Float, var age: Float = 0f)

data class Totem(var x: Float, var y: Float, var pulse: Float = 0f)

data class WeaponNode(
    val type: WeaponNodeType,
    var x: Float,
    var y: Float,
    var life: Float,
    val maxLife: Float,
    var radius: Float,
)

data class WeaponOrbital(
    val index: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
)

private enum class WeaponHitCadence { DISCRETE, CONTINUOUS }

private data class DamageResult(
    val amount: Float,
    val critical: Boolean,
)

private data class DelayedRelicHit(
    val relicId: RelicId,
    val enemyId: Int,
    var delay: Float,
    val damage: Float,
)

internal data class DomainCollectionLimit(
    val name: String,
    val size: Int,
    val maximum: Int,
)

internal class MutableGameState(
    seed: Int = 731_991,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
    bootstrapProgress: GameBootstrapSnapshot? = null,
    private val externalAuthorities: Boolean = false,
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
        const val CODEX_PAGE_SIZE = 10
        const val ARMORY_PAGE_SIZE = 3
    }

    private var gameplayRandom = CloneableXorWowRandom(seed)
    private var activeRebirthProfile = RebirthProgression.profile(
        bootstrapProgress?.rebirthLevel ?: initialRebirthLevel,
    )
    private var upcomingRebirthProfile = RebirthProgression.profile(activeRebirthProfile.tier + 1)
    private val unlockedWeaponSet = mutableSetOf<WeaponId>().apply {
        val stored = bootstrapProgress?.unlockedWeaponIndices.orEmpty()
        stored.mapNotNullTo(this) { WeaponId.entries.getOrNull(it) }
        add(WeaponId.FLUX_WAKE)
    }
    private var unlockedWeaponView: Set<WeaponId> = unlockedWeaponSet.toSet()
    private val metaRanks = IntArray(MetaUpgradeId.entries.size) { index ->
        val definition = MetaUpgradeCatalog.all[index]
        bootstrapProgress?.metaLevels?.getOrNull(index)?.coerceIn(0, definition.maxRanks) ?: 0
    }
    private val discoveredItemIds = bootstrapProgress?.discoveredItemIds
        ?.filterTo(mutableSetOf()) { it in 0 until ItemCatalog.ITEM_COUNT }
        ?: mutableSetOf()
    private val itemStacks = IntArray(ItemCatalog.ITEM_COUNT)
    private val familyStacks = IntArray(20)
    private val soundCues = mutableListOf<SoundCue>()
    private val authorityCommands = mutableListOf<GameCommand>()
    private var visualFxCues = BoundedVisualFxCueAccumulator()

    private var nextEntityId = 1
    private var spawnClock = 0f
    private var nextEliteAt = 38f
    private var dashBufferTime = 0f
    private var bossSpawned = false
    private var lastPointerPressed = false
    private var keyboardBrakeActive = false
    private var secondaryBrakeActive = false
    private var touchBrakeActive = false
    private var uiScale = 1f
    private var accumulator = 0f
    internal var lastTransitionSteps = 0
        private set
    private var previousCoreX = 0f
    private var previousCoreY = 0f
    private var previousSingularityX = 0f
    private var previousSingularityY = 0f
    private var trailLastX = 0f
    private var trailLastY = 0f
    private var trailDistanceCarry = 0f
    private var weaponClock = 0f
    private var weaponSecondaryClock = 0f
    private var pendingLevelChoices = 0
    private var pendingRelicChoices = 0
    private var pendingBindingRelic: RelicId? = null
    private var pendingRelicBindAction: RelicChoiceAction? = null
    private val relicRanks = IntArray(RelicCatalog.RELIC_COUNT)
    private val relicCooldowns = FloatArray(RelicCatalog.RELIC_COUNT)
    private val relicCounters = IntArray(RelicCatalog.RELIC_COUNT)
    private val relicProcCounts = IntArray(RelicCatalog.RELIC_COUNT)
    private val delayedRelicHits = mutableListOf<DelayedRelicHit>()
    private val agonyMutationCounts = IntArray(WeaponId.entries.size)
    private var slipstreamRelayTime = 0f
    private var borrowedMomentTime = 0f
    private var brakepointCharge = 0f
    private var dataFraction = 0f
    private var matterFraction = 0f
    private var shieldRechargeDelay = 0f
    private var overheatHoldTime = 0f
    private var saturationHeadingX = 1f
    private var saturationHeadingY = 0f
    private var timeSinceDamage = 0f
    private var hurtCooldown = 0f
    private var lastAimDirectionX = 1f
    private var lastAimDirectionY = 0f
    private var bankedThisRun = false
    private var overlayReturnPhase = GamePhase.MENU
    private var activeChoiceType = ChoiceType.ITEM

    var phase = GamePhase.MENU
        private set
    var screen = UiScreen.GAME
        private set
    var settings = bootstrapProgress?.settings ?: GameSettings()
        private set
    var rebirthLevel = activeRebirthProfile.tier
        private set
    var highestClearedRebirth = bootstrapProgress?.highestClearedRebirth
        ?.coerceIn(-1, rebirthLevel)
        ?: -1
        private set
    var rebirthConfirmationArmed = false
        private set
    var screenWidth = 1280f
        private set
    var screenHeight = 720f
        private set

    var coreX = 0f
        private set
    var coreY = 0f
        private set
    var velocityX = 0f
        private set
    var velocityY = 0f
        private set
    var cameraX = 0f
        private set
    var cameraY = 0f
        private set
    var pointerX = 900f
        private set
    var pointerY = 360f
        private set
    var pointerActive = true
        private set
    var braking = false
        private set

    var elapsed = 0f
        private set
    var heat = 0f
        private set
    var overheated = false
        private set
    var dashPhaseTime = 0f
        private set
    var hp = 100f
        private set
    var maxHp = 100f
        private set
    var shield = 0f
        private set
    var maxShield = 0f
        private set
    var level = 1
        private set
    var data = 0
        private set
    var nextLevelData = 18
        private set
    var keys = 0
        private set
    var kills = 0
        private set
    var combo = 0
        private set
    var comboTime = 0f
        private set
    var runMatter = 0L
        private set
    var totalMatter = initialMatter?.toLong() ?: bootstrapProgress?.matter ?: 0L
        private set
    var lifetimeMatter = initialMatter?.toLong() ?: bootstrapProgress?.lifetimeMatter ?: totalMatter
        private set
    var lastImpact = 0f
        private set
    var lastImpactTime = 0f
        private set
    var damageFlash = 0f
        private set
    var runGrace = 0f
        private set
    var screenShake = 0f
        private set
    var message = ""
        private set
    var messageTime = 0f
        private set

    var mass = 1f
        private set
    var damageMultiplier = 1f
        private set
    var weaponPower = 1f
        private set
    var coolingRate = 19f
        private set
    var magnetStrength = 4.65f
        private set
    var dashImpulse = 590f
        private set
    var dashHeatCost = 36f
        private set
    var regenPerSecond = 0f
        private set
    var critChance = 0.05f
        private set
    var critMultiplier = 1.5f
        private set
    var pickupRadius = 150f
        private set
    var luck = 0f
        private set
    var dataGain = 1f
        private set
    var matterGain = 1f
        private set
    var attackSpeed = 1f
        private set
    var damageReduction = 0f
        private set
    var comboWindow = 2.8f
        private set
    var overdriveGain = 1f
        private set
    var dragCoefficient = 0.29f
        private set
    var polarityStability = 1f
        private set

    var weapon = WeaponId.entries.getOrElse(bootstrapProgress?.selectedWeaponIndex ?: 0) { WeaponId.FLUX_WAKE }
        private set
    var startingWeapon = weapon
        private set
    var weaponLevel = 1
        private set
    var overdriveCharge = 0f
        private set
    var overdriveTime = 0f
        private set
    var rerollsRemaining = 1
        private set
    var acquiredItemCount = 0
        private set
    var recentItem: ItemDefinition? = null
        private set
    var equippedRelics: List<EquippedRelic> = emptyList()
        private set

    var morningstarAngle = 0f
        private set
    var morningstarX = 0f
        private set
    var morningstarY = 0f
        private set
    var weaponBeamTime = 0f
        private set
    var weaponBeamStartX = 0f
        private set
    var weaponBeamStartY = 0f
        private set
    var weaponBeamEndX = 0f
        private set
    var weaponBeamEndY = 0f
        private set

    var totem: Totem? = null
        private set
    var codexPage = 0
        private set
    var armoryPage = 0
        private set
    var settingsPage = 0
        private set
    var coreShape: CoreShape = CoreShape.ORB
        private set

    val enemies = mutableListOf<Enemy>()
    val projectiles = mutableListOf<Projectile>()
    val pickups = mutableListOf<Pickup>()
    val trail = mutableListOf<TrailPoint>()
    val weaponNodes = mutableListOf<WeaponNode>()
    val weaponOrbitals = mutableListOf<WeaponOrbital>()
    var choices: List<ChoiceOption> = emptyList()
        private set

    val speed: Float get() = length(velocityX, velocityY)
    private val threatElapsed: Float get() = elapsed + rebirthProfile.threatTimeOffsetSeconds
    val rebirthProfile: RebirthProfile get() = activeRebirthProfile
    val nextRebirthProfile: RebirthProfile get() = upcomingRebirthProfile
    val canRebirth: Boolean
        get() = rebirthLevel < RebirthProgression.MAX_LEVEL &&
            highestClearedRebirth >= rebirthLevel &&
            (phase == GamePhase.MENU || phase == GamePhase.VICTORY)
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
    val maxCodexPage: Int get() = (ItemCatalog.ITEM_COUNT - 1) / CODEX_PAGE_SIZE
    val maxArmoryPage: Int get() = (WeaponCatalog.all.size - 1) / ARMORY_PAGE_SIZE
    val codexPageItems: List<ItemDefinition>
        get() {
            val start = codexPage * CODEX_PAGE_SIZE
            return ItemCatalog.all.subList(start, min(start + CODEX_PAGE_SIZE, ItemCatalog.all.size))
        }
    val armoryPageWeapons: List<WeaponDefinition>
        get() {
            val start = armoryPage * ARMORY_PAGE_SIZE
            return WeaponCatalog.all.subList(start, min(start + ARMORY_PAGE_SIZE, WeaponCatalog.all.size))
        }

    fun relicRank(id: RelicId): Int = relicRanks[id.ordinal]

    init {
        if (startingWeapon !in unlockedWeaponSet) {
            startingWeapon = WeaponId.FLUX_WAKE
            weapon = startingWeapon
        }
        val storedShape = CoreShape.entries.getOrElse(bootstrapProgress?.coreShapeIndex ?: 0) { CoreShape.ORB }
        coreShape = if (isCoreShapeUnlocked(storedShape)) storedShape else CoreShape.ORB
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

    fun startRun() {
        screen = UiScreen.GAME
        phase = GamePhase.RUNNING
        rebirthConfirmationArmed = false
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        trail.clear()
        emitVisualFx(VisualFxCue.ClearAll)
        weaponNodes.clear()
        weaponOrbitals.clear()
        choices = emptyList()
        activeChoiceType = ChoiceType.ITEM
        equippedRelics = emptyList()
        pendingRelicChoices = 0
        pendingBindingRelic = null
        pendingRelicBindAction = null
        relicRanks.fill(0)
        relicCooldowns.fill(0f)
        relicCounters.fill(0)
        relicProcCounts.fill(0)
        agonyMutationCounts.fill(0)
        delayedRelicHits.clear()
        slipstreamRelayTime = 0f
        borrowedMomentTime = 0f
        brakepointCharge = 0f
        itemStacks.fill(0)
        familyStacks.fill(0)
        totem = null
        nextEntityId = 1
        spawnClock = 0.2f
        nextEliteAt = rebirthProfile.eliteInterval(38f)
        bossSpawned = false
        accumulator = 0f
        coreX = 0f
        coreY = 0f
        previousCoreX = 0f
        previousCoreY = 0f
        velocityX = 0f
        velocityY = 0f
        cameraX = 0f
        cameraY = 0f
        pointerX = screenWidth * 0.76f
        pointerY = screenHeight * 0.5f
        previousSingularityX = pointerX - screenWidth * 0.5f
        previousSingularityY = pointerY - screenHeight * 0.5f
        lastAimDirectionX = 1f
        lastAimDirectionY = 0f
        pointerActive = true
        keyboardBrakeActive = false
        secondaryBrakeActive = false
        touchBrakeActive = false
        updateBraking()
        dashBufferTime = 0f
        saturationHeadingX = 1f
        saturationHeadingY = 0f
        elapsed = 0f
        heat = 0f
        overheated = false
        overheatHoldTime = 0f
        dashPhaseTime = 0f
        level = 1
        data = 0
        dataFraction = 0f
        nextLevelData = 18
        pendingLevelChoices = 0
        keys = 0
        kills = 0
        combo = 0
        comboTime = 0f
        runMatter = 0L
        matterFraction = 0f
        bankedThisRun = false
        lastImpact = 0f
        lastImpactTime = 0f
        damageFlash = 0f
        hurtCooldown = 0f
        runGrace = 1.35f
        screenShake = 0f
        message = "DRIFT PHASE"
        messageTime = 2.2f
        recentItem = null
        acquiredItemCount = 0
        rerollsRemaining = 1 + metaLevel(MetaUpgradeId.DATA_ARCHIVE) / 4 + rebirthProfile.bonusRerolls
        overdriveCharge = 0f
        overdriveTime = 0f
        weaponLevel = 1
        resetRunStats()
        weapon = startingWeapon
        resetWeaponRuntime()
        emitSound(SoundCue.UI_CLICK)
        repeat(rebirthProfile.openingEnemyCount) {
            val openingType = if (rebirthLevel == 0) {
                EnemyType.DRIFTER
            } else {
                enemyTypeForElapsed(threatElapsed, gameplayRandom.nextFloat())
            }
            spawnEnemy(openingType)
        }
    }

    private fun resetRunStats() {
        mass = 1f
        damageMultiplier = 1f + metaLevel(MetaUpgradeId.KINETIC_AMPLIFIER) * 0.05f
        weaponPower = 1f + metaLevel(MetaUpgradeId.ARMORY_LICENSE) * 0.04f * unlockedWeaponSet.size
        coolingRate = 19f * (1f + metaLevel(MetaUpgradeId.CRYO_VENTS) * 0.05f)
        magnetStrength = 4.65f * (1f + metaLevel(MetaUpgradeId.MAGNETIC_RESONANCE) * 0.04f)
        dashImpulse = 590f * (1f + metaLevel(MetaUpgradeId.DASH_CAPACITOR) * 0.05f)
        dashHeatCost = 36f
        maxHp = 100f + metaLevel(MetaUpgradeId.CORE_INTEGRITY) * 10f + rebirthProfile.playerIntegrityBonus
        hp = maxHp
        regenPerSecond = 0f
        critChance = 0.05f
        critMultiplier = 1.5f
        pickupRadius = 150f
        luck = 0f
        dataGain = 1f + metaLevel(MetaUpgradeId.DATA_ARCHIVE) * 0.05f
        matterGain = (1f + metaLevel(MetaUpgradeId.SALVAGE_PROTOCOL) * 0.05f) *
            rebirthProfile.matterGainMultiplier
        attackSpeed = 1f
        maxShield = 0f
        shield = 0f
        damageReduction = 0f
        comboWindow = 2.8f
        overdriveGain = 1f
        dragCoefficient = 0.29f
        polarityStability = 1f
        shieldRechargeDelay = 0f
        timeSinceDamage = 0f
        hurtCooldown = 0f
    }

    fun returnToMenu() {
        if (phase != GamePhase.MENU) bankRunMatter()
        phase = GamePhase.MENU
        screen = UiScreen.GAME
        keyboardBrakeActive = false
        secondaryBrakeActive = false
        touchBrakeActive = false
        updateBraking()
        dashBufferTime = 0f
        accumulator = 0f
    }

    fun togglePause() {
        if (screen != UiScreen.GAME) return
        if (phase == GamePhase.RUNNING) dashBufferTime = 0f
        phase = when (phase) {
            GamePhase.RUNNING -> GamePhase.PAUSED
            GamePhase.PAUSED -> GamePhase.RUNNING
            else -> phase
        }
        updateBraking()
        accumulator = 0f
    }

    fun handleEscape() {
        if (screen != UiScreen.GAME) {
            closeOverlay()
        } else {
            togglePause()
        }
    }

    fun openSettings() {
        settingsPage = 0
        openOverlay(UiScreen.SETTINGS)
    }
    fun openLab() = openOverlay(UiScreen.LAB)
    fun openArmory() = openOverlay(UiScreen.ARMORY)
    fun openRebirth() {
        rebirthConfirmationArmed = false
        openOverlay(UiScreen.REBIRTH)
    }
    fun openCodex() = openOverlay(UiScreen.CODEX)

    private fun openOverlay(target: UiScreen) {
        if (phase == GamePhase.CHOICE) return
        if (phase in listOf(GamePhase.GAME_OVER, GamePhase.VICTORY) && target != UiScreen.REBIRTH) return
        overlayReturnPhase = if (phase == GamePhase.RUNNING) GamePhase.PAUSED else phase
        if (phase == GamePhase.RUNNING) {
            phase = GamePhase.PAUSED
            dashBufferTime = 0f
        }
        screen = target
        updateBraking()
        emitSound(SoundCue.UI_CLICK)
    }

    fun closeOverlay() {
        if (screen == UiScreen.GAME) return
        screen = UiScreen.GAME
        phase = overlayReturnPhase
        rebirthConfirmationArmed = false
        updateBraking()
        emitSound(SoundCue.UI_CLICK)
    }

    /** Arms the next threat tier first, then starts it on the second request. */
    fun requestRebirth(): Boolean {
        if (!canRebirth) return false
        if (!rebirthConfirmationArmed) {
            rebirthConfirmationArmed = true
            emitSound(SoundCue.UI_CLICK)
            return false
        }
        if (externalAuthorities) {
            authorityCommands += GameCommand.BeginRebirth(expectedLevel = rebirthLevel)
            rebirthConfirmationArmed = false
            emitSound(SoundCue.PURCHASE)
            return true
        }
        rebirthLevel++
        activeRebirthProfile = RebirthProgression.profile(rebirthLevel)
        upcomingRebirthProfile = RebirthProgression.profile(rebirthLevel + 1)
        rebirthConfirmationArmed = false
        startRun()
        message = "REBIRTH $rebirthLevel // ${rebirthProfile.directive.displayName.uppercase()}"
        messageTime = 2.8f
        emitSound(SoundCue.PURCHASE)
        return true
    }

    fun isCoreShapeUnlocked(shape: CoreShape): Boolean = lifetimeMatter >= when (shape) {
        CoreShape.ORB -> 0L
        CoreShape.PRISM -> 25L
        CoreShape.SHARD -> 90L
    }

    fun setCoreShape(shape: CoreShape) {
        if (externalAuthorities) {
            authorityCommands += GameCommand.ChangeProfile(ProfileChange.SelectCoreShape(shape))
            emitSound(SoundCue.UI_CLICK)
            return
        }
        if (!isCoreShapeUnlocked(shape)) return
        coreShape = shape
        emitSound(SoundCue.UI_CLICK)
    }

    fun updatePointer(x: Float, y: Float, active: Boolean = true) {
        pointerX = clamp(x, 0f, screenWidth)
        pointerY = clamp(y, 0f, screenHeight)
        pointerActive = active
        val targetX = cameraX + pointerX - screenWidth * 0.5f
        val targetY = cameraY + pointerY - screenHeight * 0.5f
        val dx = targetX - coreX
        val dy = targetY - coreY
        val distance = length(dx, dy)
        if (distance > 24f) {
            lastAimDirectionX = dx / distance
            lastAimDirectionY = dy / distance
        }
    }

    fun isHudControlPosition(x: Float, y: Float): Boolean =
        screen == UiScreen.GAME && phase == GamePhase.RUNNING && (inDashButton(x, y) || inBrakeButton(x, y))

    fun pointerPressed(x: Float, y: Float) {
        if (screen == UiScreen.GAME && phase == GamePhase.RUNNING && !isHudControlPosition(x, y)) {
            updatePointer(x, y)
        }
        if (lastPointerPressed) return
        lastPointerPressed = true
        if (screen != UiScreen.GAME) {
            handleOverlayPress(x, y)
            return
        }
        when (phase) {
            GamePhase.MENU -> handleMenuPress(x, y)
            GamePhase.GAME_OVER, GamePhase.VICTORY -> handleEndPress(x, y)
            GamePhase.CHOICE -> handleChoicePress(x, y)
            GamePhase.PAUSED -> handlePausePress(x, y)
            GamePhase.RUNNING -> {
                if (inDashButton(x, y)) requestDash()
                if (inBrakeButton(x, y)) {
                    touchBrakeActive = true
                    updateBraking()
                }
            }
        }
    }

    fun pointerReleased() {
        lastPointerPressed = false
        touchBrakeActive = false
        updateBraking()
    }

    fun setBrake(active: Boolean) {
        keyboardBrakeActive = active
        updateBraking()
    }

    fun setSecondaryBrake(active: Boolean) {
        secondaryBrakeActive = active
        updateBraking()
    }

    private fun updateBraking() {
        braking = phase == GamePhase.RUNNING && screen == UiScreen.GAME &&
            (keyboardBrakeActive || secondaryBrakeActive || touchBrakeActive)
    }

    fun requestDash() {
        if (phase == GamePhase.RUNNING && screen == UiScreen.GAME) {
            dashBufferTime = DASH_INPUT_BUFFER_SECONDS
        }
    }

    fun handleEnter() {
        if (screen != UiScreen.GAME) {
            closeOverlay()
            return
        }
        when (phase) {
            GamePhase.MENU, GamePhase.GAME_OVER, GamePhase.VICTORY -> startRun()
            GamePhase.PAUSED -> togglePause()
            else -> Unit
        }
    }

    fun toggleMute() {
        if (externalAuthorities) {
            authorityCommands += GameCommand.ChangeSettings(SettingsChange.ToggleMute)
            emitSound(SoundCue.UI_CLICK)
            return
        }
        val enable = !settings.soundEnabled && !settings.musicEnabled
        settings = settings.copy(soundEnabled = enable, musicEnabled = enable)
        emitSound(SoundCue.UI_CLICK)
    }

    fun choose(index: Int) {
        if (phase != GamePhase.CHOICE || index !in choices.indices) return
        val option = choices[index]
        val sound = when (option.type) {
            ChoiceType.ITEM -> {
                val itemId = option.itemId ?: return
                acquireItem(itemId)
                SoundCue.LEVEL_UP
            }
            ChoiceType.TOTEM -> when (option.totemAction ?: return) {
                TotemAction.AMPLIFY_CURRENT -> {
                    amplifyCurrentWeapon()
                    SoundCue.WEAPON_ACQUIRED
                }
                TotemAction.CHANGE_WEAPON -> {
                    openWeaponChoice()
                    emitSound(SoundCue.UI_CLICK)
                    return
                }
            }
            ChoiceType.WEAPON -> {
                val weaponId = option.weaponId ?: return
                equipRunWeapon(weaponId)
                SoundCue.WEAPON_ACQUIRED
            }
            ChoiceType.RELIC -> when (option.relicAction ?: return) {
                RelicChoiceAction.ACQUIRE -> {
                    val relicId = option.relicId ?: return
                    if (relicRank(relicId) > 0 || equippedRelics.size < RelicCatalog.MAX_SLOTS) {
                        acquireRelic(relicId)
                        SoundCue.WEAPON_ACQUIRED
                    } else {
                        pendingBindingRelic = relicId
                        pendingRelicBindAction = RelicChoiceAction.REPLACE
                        openRelicBindChoice()
                        emitSound(SoundCue.UI_CLICK)
                        return
                    }
                }
                RelicChoiceAction.MELD -> {
                    pendingBindingRelic = null
                    pendingRelicBindAction = RelicChoiceAction.MELD_TARGET
                    openRelicBindChoice()
                    emitSound(SoundCue.UI_CLICK)
                    return
                }
                RelicChoiceAction.REPLACE, RelicChoiceAction.MELD_TARGET -> return
            }
            ChoiceType.RELIC_BIND -> {
                val slot = option.relicSlot ?: return
                when (option.relicAction ?: return) {
                    RelicChoiceAction.REPLACE -> replaceRelic(slot, option.relicId ?: pendingBindingRelic ?: return)
                    RelicChoiceAction.MELD_TARGET -> meldRelic(slot)
                    RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD -> return
                }
                pendingBindingRelic = null
                pendingRelicBindAction = null
                SoundCue.WEAPON_ACQUIRED
            }
        }
        finishChoice(sound)
    }

    private fun finishChoice(sound: SoundCue) {
        choices = emptyList()
        phase = GamePhase.RUNNING
        runGrace = max(runGrace, 0.5f)
        emitSound(sound)
        openNextPendingChoice()
    }

    fun rerollChoices() {
        if (!choicesCanReroll) return
        rerollsRemaining--
        when (activeChoiceType) {
            ChoiceType.ITEM -> buildItemChoices()
            ChoiceType.WEAPON -> buildWeaponChoices()
            ChoiceType.RELIC -> buildRelicChoices()
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> return
        }
        emitSound(SoundCue.UI_CLICK)
    }

    fun update(rawDelta: Float) {
        lastTransitionSteps = 0
        if (phase != GamePhase.RUNNING || screen != UiScreen.GAME) {
            accumulator = 0f
            return
        }
        if (!rawDelta.isFinite()) {
            accumulator = 0f
            return
        }
        val scaledDelta = rawDelta.coerceIn(0f, 0.1f) * settings.simulationSpeed
        accumulator = min(0.3f, accumulator + scaledDelta)
        var steps = 0
        while (accumulator >= FIXED_STEP && phase == GamePhase.RUNNING && steps < 48) {
            simulateStep(FIXED_STEP)
            accumulator -= FIXED_STEP
            steps++
        }
        lastTransitionSteps = steps
    }

    private fun simulateStep(delta: Float) {
        elapsed += delta
        runGrace = max(0f, runGrace - delta)
        hurtCooldown = max(0f, hurtCooldown - delta)
        dashPhaseTime = max(0f, dashPhaseTime - delta)
        screenShake = max(0f, screenShake - delta * 18f)
        lastImpactTime = max(0f, lastImpactTime - delta)
        damageFlash = max(0f, damageFlash - delta * 3.4f)
        messageTime = max(0f, messageTime - delta)
        comboTime = max(0f, comboTime - delta)
        weaponBeamTime = max(0f, weaponBeamTime - delta)
        if (comboTime <= 0f) combo = 0

        updateSurvival(delta)
        updateOverdrive(delta)
        updateHeat(delta)
        updateCore(delta)
        if (phase != GamePhase.RUNNING) return
        emitVisualFx(
            VisualFxCue.MotionSample(
                deltaSeconds = delta,
                previousCoreX = previousCoreX,
                previousCoreY = previousCoreY,
                speed = speed,
                dashPhaseTime = dashPhaseTime,
            ),
        )
        updateCamera(delta)
        updateWeapons(delta)
        updateEnemies(delta)
        updateRelicRuntime(delta)
        updateProjectiles(delta)
        updatePickups(delta)
        updateTotem(delta)
        emitVisualFx(VisualFxCue.EffectsAdvanced(delta))
        spawnWave(delta)
        resolveCollisions(delta)
        if (phase != GamePhase.RUNNING) return
        rebaseWorldIfNeeded()

        openNextPendingChoice()

        if (!bossSpawned && phase == GamePhase.RUNNING && elapsed >= RUN_DURATION_SECONDS) {
            bossSpawned = true
            message = "THE ARCHITECT"
            messageTime = 3f
            projectiles.removeAll { it.hostile }
            // The terminal boss has priority over the oldest ordinary enemy at the hard cap.
            if (enemies.size >= MAX_ENEMIES) enemies.removeAt(0)
            spawnEnemy(EnemyType.ARCHITECT)
        }
    }

    private fun updateSurvival(delta: Float) {
        timeSinceDamage += delta
        shieldRechargeDelay = max(0f, shieldRechargeDelay - delta)
        if (regenPerSecond > 0f && timeSinceDamage >= 3f) hp = min(maxHp, hp + regenPerSecond * delta)
        if (maxShield > 0f && shieldRechargeDelay <= 0f) {
            shield = min(maxShield, shield + max(2f, maxShield * 0.09f) * delta)
        }
    }

    private fun updateRelicRuntime(delta: Float) {
        relicCooldowns.indices.forEach { index ->
            relicCooldowns[index] = max(0f, relicCooldowns[index] - delta)
        }
        slipstreamRelayTime = max(0f, slipstreamRelayTime - delta)
        borrowedMomentTime = max(0f, borrowedMomentTime - delta)
        val brakeRank = relicRank(RelicId.BRAKEPOINT_MEMORY)
        if (brakeRank > 0 && braking) {
            val cap = 0.18f * brakeRank
            brakepointCharge = min(cap, brakepointCharge + cap * delta)
        }

        val glassIndex = RelicId.GLASS_WITNESS.ordinal
        val scarIndex = RelicId.SCAR_TISSUE.ordinal
        val scarRank = relicRank(RelicId.SCAR_TISSUE)
        enemies.forEach { enemy ->
            enemy.relicQualificationCooldown = max(0f, enemy.relicQualificationCooldown - delta)
            enemy.relicTimers[glassIndex] = max(0f, enemy.relicTimers[glassIndex] - delta)
            if (enemy.relicTimers[scarIndex] > 0f) {
                enemy.relicTimers[scarIndex] = max(0f, enemy.relicTimers[scarIndex] - delta)
                if (scarRank > 0 && enemy.hp > 0f) {
                    val intensity = max(1, enemy.relicCounters[scarIndex])
                    damageEnemy(enemy, 3f * scarRank * intensity * delta)
                }
            }
        }

        val iterator = delayedRelicHits.iterator()
        while (iterator.hasNext()) {
            val delayed = iterator.next()
            delayed.delay -= delta
            if (delayed.delay > 0f) continue
            val target = enemies.firstOrNull { it.id == delayed.enemyId && !it.dead && it.hp > 0f }
            if (target != null && relicRank(delayed.relicId) > 0) {
                damageEnemy(target, delayed.damage)
                relicProcCounts[delayed.relicId.ordinal]++
                burst(target.x, target.y, 4, 2)
            }
            iterator.remove()
        }
    }

    private fun updateOverdrive(delta: Float) {
        if (overdriveTime > 0f) {
            overdriveTime = max(0f, overdriveTime - delta)
        } else if (overdriveCharge >= 100f) {
            overdriveCharge -= 100f
            overdriveTime = 7f
            val paradoxRank = relicRank(RelicId.ENGINE_OF_PARADOX)
            if (paradoxRank > 0) {
                weaponClock = 0f
                weaponSecondaryClock = 0f
                heat = max(0f, heat - 10f)
                relicProcCounts[RelicId.ENGINE_OF_PARADOX.ordinal]++
            }
            message = "KINETIC OVERDRIVE"
            messageTime = 2f
            emitSound(SoundCue.OVERDRIVE)
        }
        if (speed > 450f) {
            val gain = min(5f, (speed - 450f) / 900f) * delta * 8f * overdriveGain
            overdriveCharge = min(125f, overdriveCharge + gain)
        }
    }

    private fun updateHeat(delta: Float) {
        val cooling = coolingRate * if (braking) 1.6f else 1f
        overheatHoldTime = max(0f, overheatHoldTime - delta)
        if (overheatHoldTime <= 0f) heat = max(0f, heat - cooling * delta)
        if (overheated && heat <= 28f) {
            overheated = false
            message = "DASH ONLINE"
            messageTime = 1f
            emitSound(SoundCue.RECOVERED)
        }
        dashBufferTime = max(0f, dashBufferTime - delta)
        if (dashBufferTime > 0f && dashReady) {
            dashBufferTime = 0f
            performDash()
        }
    }

    private fun performDash() {
        val departureX = coreX
        val departureY = coreY
        val targetX = cameraX + pointerX - screenWidth * 0.5f
        val targetY = cameraY + pointerY - screenHeight * 0.5f
        val dx = targetX - coreX
        val dy = targetY - coreY
        val distance = length(dx, dy)
        val directionX = if (distance > 24f) dx / distance else lastAimDirectionX
        val directionY = if (distance > 24f) dy / distance else lastAimDirectionY
        val forwardVelocity = velocityX * directionX + velocityY * directionY
        if (forwardVelocity < 0f) {
            val reverseAssist = forwardVelocity * 0.72f
            velocityX -= directionX * reverseAssist
            velocityY -= directionY * reverseAssist
        }
        velocityX += directionX * dashImpulse
        velocityY += directionY * dashImpulse
        heat += dashHeatCost
        dashPhaseTime = 0.24f
        screenShake = max(screenShake, 5f)
        overdriveCharge = min(125f, overdriveCharge + 5f * overdriveGain)
        shockwave(coreX, coreY, 0.3f, 112f, 0)
        directionalBurst(coreX, coreY, 18, 0, -directionX, -directionY)
        val ghostRank = relicRank(RelicId.GHOST_VECTOR)
        if (ghostRank > 0) {
            val radius = 72f + 14f * ghostRank
            enemies.forEach { enemy ->
                if (!enemy.dead && enemy.hp > 0f && distanceSquared(departureX, departureY, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
                    damageEnemy(enemy, 24f * ghostRank)
                }
            }
            shockwave(departureX, departureY, 0.22f, radius, 2)
            relicProcCounts[RelicId.GHOST_VECTOR.ordinal]++
        }
        emitSound(SoundCue.DASH)
        if (heat >= MAX_HEAT) {
            heat = MAX_HEAT
            overheated = true
            overheatHoldTime = 0.08f
            message = "OVERHEAT"
            messageTime = 1.4f
            emitSound(SoundCue.OVERHEAT)
        }
    }

    private fun updateCore(delta: Float) {
        previousCoreX = coreX
        previousCoreY = coreY
        val targetX = cameraX + pointerX - screenWidth * 0.5f
        val targetY = cameraY + pointerY - screenHeight * 0.5f
        val dx = targetX - coreX
        val dy = targetY - coreY
        val distance = max(0.001f, length(dx, dy))
        if (distance > 24f) {
            lastAimDirectionX = dx / distance
            lastAimDirectionY = dy / distance
        }
        val directionX = dx / distance
        val directionY = dy / distance
        updatePolarityStability(directionX, directionY, delta)
        val overdriveMultiplier = if (overdriveTime > 0f) 1.3f else 1f
        val pull = (92f + distance * magnetStrength) * overdriveMultiplier * tetherAuthority
        val brakeFactor = if (braking) 1.12f else 1f
        velocityX += directionX * pull * brakeFactor * delta
        velocityY += directionY * pull * brakeFactor * delta
        val damping = exp((if (braking) -5.2f else -dragCoefficient) * delta)
        velocityX *= damping
        velocityY *= damping

        if (!velocityX.isFinite() || !velocityY.isFinite()) {
            velocityX = 0f
            velocityY = 0f
        }
        coreX += velocityX * delta
        coreY += velocityY * delta

        if (!coreX.isFinite() || !coreY.isFinite()) {
            coreX = previousCoreX
            coreY = previousCoreY
            velocityX = 0f
            velocityY = 0f
        }

        if (runGrace <= 0f && segmentCircleIntersects(
                previousCoreX - previousSingularityX,
                previousCoreY - previousSingularityY,
                coreX - targetX,
                coreY - targetY,
                0f,
                0f,
                CURSOR_KILL_RADIUS,
            )
        ) {
            endRun("SINGULARITY CONTACT")
        }
        previousSingularityX = targetX
        previousSingularityY = targetY
    }

    private fun updatePolarityStability(
        directionX: Float,
        directionY: Float,
        delta: Float,
    ) {
        val halfWidth = max(1f, screenWidth * 0.5f)
        val halfHeight = max(1f, screenHeight * 0.5f)
        val normalizedX = (pointerX - halfWidth) / halfWidth
        val normalizedY = (pointerY - halfHeight) / halfHeight
        val reach = clamp(length(normalizedX, normalizedY), 0f, 1f)
        val load = reach * reach
        val headingDot = clamp(
            saturationHeadingX * directionX + saturationHeadingY * directionY,
            -1f,
            1f,
        )
        val headingCross = saturationHeadingX * directionY - saturationHeadingY * directionX
        val turnAngle = kotlin.math.abs(atan2(headingCross, headingDot))
        val meaningfulTurn = max(0f, turnAngle - 0.15f)
        val nearRecovery = clamp((0.35f - reach) / (0.35f - 0.18f), 0f, 1f)
        val stabilityBefore = polarityStability
        val saturation = clamp(
            (1f - polarityStability) +
                0.40f * load * delta -
                0.85f * nearRecovery * delta -
                0.58f * meaningfulTurn,
            0f,
            1f,
        )
        polarityStability = 1f - saturation
        if (turnAngle > 0.15f) {
            saturationHeadingX = directionX
            saturationHeadingY = directionY
        }
        if (stabilityBefore >= 0.35f && polarityStability < 0.35f) {
            message = "POLARITY FIELD STRAIN"
            messageTime = 1.4f
            emitSound(SoundCue.OVERHEAT)
        }
    }

    private fun updateCamera(delta: Float) {
        val factor = 1f - exp(-7.5f * delta)
        cameraX = lerp(cameraX, coreX, factor)
        cameraY = lerp(cameraY, coreY, factor)
    }

    private fun updateWeapons(delta: Float) {
        weaponClock -= delta
        weaponSecondaryClock -= delta
        val power = effectiveWeaponPower()
        val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
        when (weapon) {
            WeaponId.FLUX_WAKE -> sampleFluxTrail()
            WeaponId.MORNINGSTAR -> {
                val angularSpeed = 3.2f + softVelocity(speed) / 135f + agonyRank * 0.12f
                morningstarAngle = (morningstarAngle + angularSpeed * delta) % TAU
                val radius = 104f + min(70f, softVelocity(speed) * 0.055f) + agonyRank * 4f
                morningstarX = coreX + cos(morningstarAngle) * radius
                morningstarY = coreY + sin(morningstarAngle) * radius
                if (agonyRank > 0) agonyMutationCounts[WeaponId.MORNINGSTAR.ordinal]++
            }
            WeaponId.PHASE_LATTICE -> if (agonyRank > 0) agonyMutationCounts[WeaponId.PHASE_LATTICE.ordinal]++
            WeaponId.NULL_LANCE -> {
                if (weaponClock <= 0f) {
                    weaponClock = cooldown(1.15f)
                    val angle = movementAngle()
                    firePlayerProjectile(coreX, coreY, angle, 940f + softVelocity(speed) * 0.22f, 56f * power, 5, 6f, 0)
                    if (agonyRank > 0) {
                        firePlayerProjectile(coreX, coreY, angle + TAU * 0.5f, 820f, 18f * agonyRank * power, 2 + agonyRank, 5f, 4)
                        agonyMutationCounts[WeaponId.NULL_LANCE.ordinal]++
                    }
                    emitSound(SoundCue.WEAPON_LIGHT)
                }
            }
            WeaponId.GRAVITY_MINES -> {
                if (weaponClock <= 0f && (braking || speed > 220f)) {
                    weaponClock = cooldown(if (braking) 0.72f else 1.15f)
                    if (weaponNodes.count { it.type == WeaponNodeType.GRAVITY_MINE } < 8) {
                        val mineLife = 0.75f + agonyRank * 0.04f
                        weaponNodes += WeaponNode(WeaponNodeType.GRAVITY_MINE, coreX, coreY, mineLife, mineLife, 96f + agonyRank * 12f)
                        if (agonyRank > 0) agonyMutationCounts[WeaponId.GRAVITY_MINES.ordinal]++
                        emitSound(SoundCue.WEAPON_LIGHT)
                    }
                }
            }
            WeaponId.ION_SWARM -> {
                val orbitalCount = min(8, 2 + (weaponLevel - 1) / 3 + if (agonyRank > 0) 1 + agonyRank / 2 else 0)
                ensureOrbitals(orbitalCount, 145f + agonyRank * 3f, 8f, delta, 1.8f + agonyRank * 0.05f)
                if (agonyRank > 0) agonyMutationCounts[WeaponId.ION_SWARM.ordinal]++
                if (weaponClock <= 0f && enemies.isNotEmpty()) {
                    weaponClock = cooldown(0.72f)
                    weaponOrbitals.forEach { orbital ->
                        nearestEnemy(orbital.x, orbital.y, 620f)?.let { enemy ->
                            val angle = atan2(enemy.y - orbital.y, enemy.x - orbital.x)
                            firePlayerProjectile(orbital.x, orbital.y, angle, 620f, 15f * power, 0, 4f, 1)
                        }
                    }
                    emitSound(SoundCue.WEAPON_LIGHT)
                }
            }
            WeaponId.RIFT_BLADES -> {
                val bladeCount = min(8, 2 + (weaponLevel - 1) / 3 + if (agonyRank > 0) 1 + agonyRank / 2 else 0)
                ensureOrbitals(bladeCount, 82f + min(75f, softVelocity(speed) * 0.025f) + agonyRank * 5f, 17f, delta, 4.2f + agonyRank * 0.08f)
                if (agonyRank > 0) agonyMutationCounts[WeaponId.RIFT_BLADES.ordinal]++
            }
            WeaponId.ARC_COIL -> {
                if (weaponClock <= 0f && enemies.isNotEmpty()) {
                    weaponClock = cooldown(0.88f)
                    fireArcCoil(35f * power)
                    emitSound(SoundCue.WEAPON_LIGHT)
                }
            }
            WeaponId.QUASAR_CANNON -> {
                if (weaponClock <= 0f) {
                    weaponClock = cooldown(1.65f)
                    val angle = movementAngle()
                    firePlayerProjectile(coreX, coreY, angle, 690f, 135f * power, 10, 14f, 2)
                    if (agonyRank > 0) {
                        val sideDamage = 28f * agonyRank * power
                        firePlayerProjectile(coreX, coreY, angle - 0.16f, 760f, sideDamage, 3, 7f, 4)
                        firePlayerProjectile(coreX, coreY, angle + 0.16f, 760f, sideDamage, 3, 7f, 4)
                        agonyMutationCounts[WeaponId.QUASAR_CANNON.ordinal]++
                    }
                    screenShake = max(screenShake, 4f)
                    emitSound(SoundCue.WEAPON_HEAVY)
                }
            }
            WeaponId.ENTROPY_FIELD -> if (agonyRank > 0) agonyMutationCounts[WeaponId.ENTROPY_FIELD.ordinal]++
            WeaponId.SINGULARITY_SPEAR -> {
                if (weaponClock <= 0f) {
                    weaponClock = cooldown(if (overdriveTime > 0f) 0.9f else 3.2f)
                    fireSingularitySpear(185f * power)
                }
            }
            WeaponId.PRISM_RELAY -> {
                if (weaponClock <= 0f && enemies.isNotEmpty()) {
                    weaponClock = cooldown(0.92f)
                    firePrismRelay(power)
                }
            }
        }

        trail.forEach { it.age += delta }
        trail.removeAll { it.age > 2.25f }
        updateWeaponNodes(delta)
        emitVisualFx(VisualFxCue.WeaponArcsAdvanced(delta))
    }

    private fun sampleFluxTrail() {
        if (speed <= 45f) {
            trailLastX = coreX
            trailLastY = coreY
            return
        }
        val dx = coreX - trailLastX
        val dy = coreY - trailLastY
        val distance = length(dx, dy)
        if (distance <= 0f) return
        var sampleDistance = 22f - trailDistanceCarry
        var samples = 0
        while (sampleDistance <= distance && samples < 32) {
            val amount = sampleDistance / distance
            trail += TrailPoint(lerp(trailLastX, coreX, amount), lerp(trailLastY, coreY, amount))
            sampleDistance += 22f
            samples++
        }
        trailDistanceCarry = (trailDistanceCarry + distance) % 22f
        while (trail.size > MAX_TRAIL_POINTS) trail.removeAt(0)
        trailLastX = coreX
        trailLastY = coreY
        if (relicRank(RelicId.AGONY_SCEPTER) > 0) agonyMutationCounts[WeaponId.FLUX_WAKE.ordinal]++
    }

    private fun ensureOrbitals(count: Int, orbitRadius: Float, hitRadius: Float, delta: Float, angularSpeed: Float) {
        if (weaponOrbitals.size != count || weaponOrbitals.any { it.radius != hitRadius }) {
            weaponOrbitals.clear()
            repeat(count) { weaponOrbitals += WeaponOrbital(it, coreX, coreY, hitRadius) }
        }
        weaponOrbitals.forEach { orbital ->
            val angle = elapsed * angularSpeed + orbital.index * TAU / max(1, count)
            orbital.x = coreX + cos(angle) * orbitRadius
            orbital.y = coreY + sin(angle) * orbitRadius
        }
    }

    private fun updateWeaponNodes(delta: Float) {
        val iterator = weaponNodes.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            node.life -= delta
            if (node.life <= 0f) {
                explodeMine(node)
                iterator.remove()
            }
        }
    }

    private fun explodeMine(node: WeaponNode) {
        val power = effectiveWeaponPower()
        enemies.forEach { enemy ->
            val distance = length(enemy.x - node.x, enemy.y - node.y)
            if (distance <= node.radius + enemy.radius) {
                dealWeaponDamage(enemy, 62f * power * (1f - distance / (node.radius * 1.7f)).coerceAtLeast(0.35f), canCrit = true)
                val pull = (1f - distance / max(1f, node.radius)).coerceAtLeast(0f) * 170f
                enemy.vx += (node.x - enemy.x) / max(1f, distance) * pull
                enemy.vy += (node.y - enemy.y) / max(1f, distance) * pull
            }
        }
        burst(node.x, node.y, 18, 2)
        screenShake = max(screenShake, 5f)
        emitSound(SoundCue.IMPACT)
    }

    private fun fireArcCoil(baseDamage: Float) {
        val targets = enemies.asSequence()
            .filter { !it.dead && it.hp > 0f && distanceSquared(coreX, coreY, it.x, it.y) <= 560f * 560f }
            .sortedBy { distanceSquared(coreX, coreY, it.x, it.y) }
            .take(min(6, 3 + weaponLevel / 3))
            .toList()
        var fromX = coreX
        var fromY = coreY
        targets.forEachIndexed { index, enemy ->
            dealWeaponDamage(enemy, baseDamage * powFast(0.76f, index), canCrit = true)
            addWeaponArc(fromX, fromY, enemy.x, enemy.y)
            fromX = enemy.x
            fromY = enemy.y
        }
        val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
        if (agonyRank > 0 && targets.isNotEmpty()) {
            val first = targets.first()
            dealWeaponDamage(first, baseDamage * 0.18f * agonyRank, canCrit = true)
            addRelicArc(fromX, fromY, first.x, first.y)
            agonyMutationCounts[WeaponId.ARC_COIL.ordinal]++
        }
    }

    private fun fireSingularitySpear(damage: Float) {
        val angle = movementAngle()
        val length = 900f + softVelocity(speed) * 0.18f
        weaponBeamStartX = coreX
        weaponBeamStartY = coreY
        weaponBeamEndX = coreX + cos(angle) * length
        weaponBeamEndY = coreY + sin(angle) * length
        weaponBeamTime = 0.18f
        enemies.forEach { enemy ->
            if (segmentCircleIntersects(
                    weaponBeamStartX,
                    weaponBeamStartY,
                    weaponBeamEndX,
                    weaponBeamEndY,
                    enemy.x,
                    enemy.y,
                    enemy.radius + 15f,
                )
            ) {
                dealWeaponDamage(enemy, damage, canCrit = true)
            }
        }
        val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
        if (agonyRank > 0) {
            val crossAngle = angle + TAU * 0.25f
            val crossEndX = coreX + cos(crossAngle) * length * 0.72f
            val crossEndY = coreY + sin(crossAngle) * length * 0.72f
            enemies.forEach { enemy ->
                if (segmentCircleIntersects(coreX, coreY, crossEndX, crossEndY, enemy.x, enemy.y, enemy.radius + 12f)) {
                    dealWeaponDamage(enemy, damage * 0.28f * agonyRank, canCrit = true)
                }
            }
            addWeaponArc(coreX, coreY, crossEndX, crossEndY, 0.18f)
            agonyMutationCounts[WeaponId.SINGULARITY_SPEAR.ordinal]++
        }
        screenShake = max(screenShake, 7f)
        emitSound(SoundCue.WEAPON_HEAVY)
    }

    private fun firePrismRelay(power: Float) {
        val target = nearestEnemy(coreX, coreY, 820f) ?: return
        val baseAngle = atan2(target.y - coreY, target.x - coreX)
        val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
        val relayCount = (if (currentWeaponMastery >= WeaponMastery.RESONANT) 2 else 1) + if (agonyRank > 0) 1 else 0
        val bounces = when (currentWeaponMastery) {
            WeaponMastery.CALIBRATED -> 2
            WeaponMastery.AMPLIFIED -> 3
            WeaponMastery.RESONANT -> 4
            WeaponMastery.ASCENDED -> 6
        } + agonyRank
        repeat(relayCount) { index ->
            val offset = (index - (relayCount - 1) * 0.5f) * 0.075f
            firePlayerProjectile(coreX, coreY, baseAngle + offset, 760f, 27f * power, bounces, 6f, 3)
        }
        if (agonyRank > 0) agonyMutationCounts[WeaponId.PRISM_RELAY.ordinal]++
        emitSound(SoundCue.WEAPON_LIGHT)
    }

    private fun updateEnemies(delta: Float) {
        enemies.forEach { enemy ->
            enemy.previousX = enemy.x
            enemy.previousY = enemy.y
            enemy.flash = max(0f, enemy.flash - delta * 5f)
            enemy.contactCooldown = max(0f, enemy.contactCooldown - delta)
            enemy.weaponCooldown = max(0f, enemy.weaponCooldown - delta)
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = max(1f, length(dx, dy))
            when (enemy.type) {
                EnemyType.DRIFTER -> steerEnemy(enemy, dx / distance * 78f, dy / distance * 78f, 2.1f, delta)
                EnemyType.SHOOTER -> {
                    val desired = if (distance > 340f) 78f else if (distance < 250f) -72f else 0f
                    val tangent = if (enemy.id % 2 == 0) 1f else -1f
                    steerEnemy(
                        enemy,
                        dx / distance * desired - dy / distance * 62f * tangent,
                        dy / distance * desired + dx / distance * 62f * tangent,
                        2.4f,
                        delta,
                    )
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= 0f && distance < 760f) {
                        enemy.actionTimer = max(0.62f, 1.7f - threatElapsed / 900f)
                        fireSpread(enemy.x, enemy.y, atan2(dy, dx), if (threatElapsed > 420f) 3 else 1, 0.14f, 220f)
                    }
                }
                EnemyType.CHARGER -> {
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= -0.45f) {
                        enemy.actionTimer = 2.15f
                        enemy.vx = dx / distance * 390f * rebirthProfile.enemySpeedMultiplier
                        enemy.vy = dy / distance * 390f * rebirthProfile.enemySpeedMultiplier
                    } else if (enemy.actionTimer < 0f) {
                        enemy.vx *= exp(-6f * delta)
                        enemy.vy *= exp(-6f * delta)
                    } else {
                        steerEnemy(enemy, dx / distance * 48f, dy / distance * 48f, 0.7f, delta)
                    }
                }
                EnemyType.INTERCEPTOR -> {
                    val leadTime = clamp(distance / 650f, 0.28f, 0.85f)
                    val leadX = coreX + velocityX * leadTime - enemy.x
                    val leadY = coreY + velocityY * leadTime - enemy.y
                    val leadDistance = max(1f, length(leadX, leadY))
                    val interceptSpeed = min(360f, 145f + speed * 0.13f)
                    steerEnemy(
                        enemy,
                        leadX / leadDistance * interceptSpeed,
                        leadY / leadDistance * interceptSpeed,
                        4.2f,
                        delta,
                    )
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= 0f && distance < 720f) {
                        enemy.actionTimer = 2.4f
                        val interceptBoost = 175f * rebirthProfile.enemySpeedMultiplier
                        enemy.vx += leadX / leadDistance * interceptBoost
                        enemy.vy += leadY / leadDistance * interceptBoost
                    }
                }
                EnemyType.WEAVER -> {
                    val weave = sin(elapsed * 2.7f + enemy.id * 0.91f)
                    val tangentX = -dy / distance
                    val tangentY = dx / distance
                    steerEnemy(
                        enemy,
                        dx / distance * 92f + tangentX * weave * 175f,
                        dy / distance * 92f + tangentY * weave * 175f,
                        2.8f,
                        delta,
                    )
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= 0f && distance < 700f) {
                        enemy.actionTimer = 1.85f
                        fireProjectileWall(
                            enemy.x,
                            enemy.y,
                            atan2(dy, dx),
                            if (threatElapsed > 420f) 5 else 3,
                            34f,
                            245f,
                        )
                    }
                }
                EnemyType.WARDEN -> {
                    val desired = if (distance > 330f) 64f else if (distance < 250f) -58f else 0f
                    val tangent = if (enemy.id % 2 == 0) 1f else -1f
                    steerEnemy(
                        enemy,
                        dx / distance * desired - dy / distance * 28f * tangent,
                        dy / distance * desired + dx / distance * 28f * tangent,
                        1.55f,
                        delta,
                    )
                    if (distance < 440f && dashPhaseTime <= 0f) {
                        val gravity = 38f + (1f - distance / 440f) * 270f
                        velocityX -= dx / distance * gravity * delta
                        velocityY -= dy / distance * gravity * delta
                    }
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= 0f && distance < 780f) {
                        enemy.actionTimer = 2.65f
                        fireRadial(enemy.x, enemy.y, 8, 132f, elapsed * 0.35f)
                    }
                }
                EnemyType.SPLITTER -> {
                    val sway = sin(elapsed * 1.6f + enemy.id) * 44f
                    steerEnemy(
                        enemy,
                        dx / distance * 58f - dy / distance * sway,
                        dy / distance * 58f + dx / distance * sway,
                        1.35f,
                        delta,
                    )
                }
                EnemyType.ELITE -> {
                    val tangent = if (enemy.id % 2 == 0) 1f else -1f
                    steerEnemy(enemy, dx / distance * 52f - dy / distance * 45f * tangent, dy / distance * 52f + dx / distance * 45f * tangent, 1.3f, delta)
                    enemy.actionTimer -= delta
                    if (enemy.actionTimer <= 0f) {
                        enemy.actionTimer = 1.18f
                        fireRadial(enemy.x, enemy.y, 10, 165f, elapsed * 0.2f)
                    }
                }
                EnemyType.ARCHITECT -> updateArchitect(enemy, dx, dy, distance, delta)
            }
            enemy.x += enemy.vx * delta
            enemy.y += enemy.vy * delta
        }
        val leashDistance = max(screenWidth, screenHeight) * 1.15f + 360f
        val leashSquared = leashDistance * leashDistance
        enemies.removeAll { enemy ->
            enemy.type != EnemyType.ELITE &&
                enemy.type != EnemyType.ARCHITECT &&
                distanceSquared(enemy.x, enemy.y, coreX, coreY) > leashSquared
        }
    }

    private fun updateArchitect(enemy: Enemy, dx: Float, dy: Float, distance: Float, delta: Float) {
        val orbit = elapsed * 0.23f
        val targetX = coreX + cos(orbit) * 380f
        val targetY = coreY + sin(orbit) * 380f
        steerEnemy(enemy, (targetX - enemy.x) * 0.6f, (targetY - enemy.y) * 0.6f, 1.2f, delta)
        enemy.actionTimer -= delta
        if (enemy.actionTimer <= 0f) {
            enemy.actionTimer = if (enemy.hp < enemy.maxHp * 0.5f) 0.5f else 0.78f
            fireSpread(enemy.x, enemy.y, atan2(dy, dx), 7, 0.18f, 250f)
            fireRadial(enemy.x, enemy.y, if (enemy.hp < enemy.maxHp * 0.5f) 18 else 12, 135f, elapsed)
        }
        if (distance < 180f) {
            enemy.vx -= dx / distance * 80f * delta
            enemy.vy -= dy / distance * 80f * delta
        }
    }

    private fun steerEnemy(enemy: Enemy, desiredX: Float, desiredY: Float, agility: Float, delta: Float) {
        val factor = 1f - exp(-agility * delta)
        enemy.vx = lerp(enemy.vx, desiredX * rebirthProfile.enemySpeedMultiplier, factor)
        enemy.vy = lerp(enemy.vy, desiredY * rebirthProfile.enemySpeedMultiplier, factor)
    }

    private fun updateProjectiles(delta: Float) {
        projectiles.forEach {
            it.previousX = it.x
            it.previousY = it.y
            it.x += it.vx * delta
            it.y += it.vy * delta
            it.life -= delta
        }
        projectiles.removeAll {
            it.life <= 0f || distanceSquared(it.x, it.y, coreX, coreY) > 1_600f * 1_600f
        }
        while (projectiles.size > MAX_PROJECTILES) projectiles.removeAt(0)
    }

    private fun updatePickups(delta: Float) {
        pickups.forEach { pickup ->
            pickup.previousX = pickup.x
            pickup.previousY = pickup.y
            pickup.life -= delta
            val dx = coreX - pickup.x
            val dy = coreY - pickup.y
            val distance = max(1f, length(dx, dy))
            if (distance < pickupRadius) {
                val pull = (pickupRadius - distance) * 4.8f
                pickup.vx += dx / distance * pull * delta
                pickup.vy += dy / distance * pull * delta
            }
            pickup.vx *= exp(-1.8f * delta)
            pickup.vy *= exp(-1.8f * delta)
            pickup.x += pickup.vx * delta
            pickup.y += pickup.vy * delta
        }
        pickups.removeAll { it.life <= 0f }
        while (pickups.size > MAX_PICKUPS) pickups.removeAt(0)
    }

    private fun updateTotem(delta: Float) {
        val activeTotem = totem
        if (activeTotem != null) {
            activeTotem.pulse = (activeTotem.pulse + delta * 2.5f) % TAU
            if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, activeTotem.x, activeTotem.y, 45f) && keys > 0) {
                keys--
                totem = null
                openTotemChoice()
            }
        } else if (keys > 0 && phase == GamePhase.RUNNING) {
            val angle = gameplayRandom.nextFloat() * TAU
            val distance = 520f + gameplayRandom.nextFloat() * 180f
            totem = Totem(coreX + cos(angle) * distance, coreY + sin(angle) * distance)
            message = "TOTEM DETECTED"
            messageTime = 1.5f
        }
    }

    private fun spawnWave(delta: Float) {
        if (bossSpawned) return
        spawnClock -= delta
        val baseMaxEnemies = min(90, 14 + floor(elapsed / 20f).toInt())
        val maxEnemies = min(MAX_ENEMIES, rebirthProfile.enemyCap(baseMaxEnemies))
        if (spawnClock <= 0f && enemies.size < maxEnemies) {
            val baseInterval = max(0.13f, 0.84f - elapsed / 1_700f)
            spawnClock = rebirthProfile.spawnInterval(baseInterval)
            val rolledType = enemyTypeForElapsed(threatElapsed, gameplayRandom.nextFloat())
            val maxWardens = 2 + min(4, rebirthLevel / 3)
            val type = if (rolledType == EnemyType.WARDEN && enemies.count { it.type == EnemyType.WARDEN } >= maxWardens) {
                EnemyType.WEAVER
            } else {
                rolledType
            }
            spawnEnemy(type)
        }
        if (elapsed >= nextEliteAt) {
            nextEliteAt += rebirthProfile.eliteInterval(max(48f, 86f - elapsed / 25f))
            if (spawnEnemy(EnemyType.ELITE)) {
                message = "ELITE SIGNAL"
                messageTime = 1.4f
            }
        }
    }

    private fun spawnEnemy(type: EnemyType): Boolean {
        if (enemies.size >= MAX_ENEMIES) return false
        val useForwardCorridor = type != EnemyType.ELITE &&
            type != EnemyType.ARCHITECT &&
            speed > 500f &&
            gameplayRandom.nextFloat() < 0.82f
        val angle = if (useForwardCorridor) {
            movementAngle()
        } else {
            gameplayRandom.nextFloat() * TAU
        }
        val directionX = cos(angle)
        val directionY = sin(angle)
        val distanceToVerticalEdge = screenWidth * 0.5f / max(0.001f, kotlin.math.abs(directionX))
        val distanceToHorizontalEdge = screenHeight * 0.5f / max(0.001f, kotlin.math.abs(directionY))
        val viewportEdgeDistance = min(distanceToVerticalEdge, distanceToHorizontalEdge)
        val distance = viewportEdgeDistance + 90f + gameplayRandom.nextFloat() * 150f
        val lateralOffset = if (useForwardCorridor) (gameplayRandom.nextFloat() - 0.5f) * 140f else 0f
        val x = coreX + directionX * distance - directionY * lateralOffset
        val y = coreY + directionY * distance + directionX * lateralOffset
        val difficulty = 1f + elapsed / 470f
        val stats = when (type) {
            EnemyType.DRIFTER -> Triple(rebirthProfile.enemyHealth(30f * difficulty), 17f, 0.2f)
            EnemyType.SHOOTER -> Triple(rebirthProfile.enemyHealth(46f * difficulty), 20f, 0.7f)
            EnemyType.CHARGER -> Triple(rebirthProfile.enemyHealth(68f * difficulty), 23f, 1.2f)
            EnemyType.INTERCEPTOR -> Triple(rebirthProfile.enemyHealth(58f * difficulty), 18f, 1.4f)
            EnemyType.WEAVER -> Triple(rebirthProfile.enemyHealth(74f * difficulty), 21f, 0.9f)
            EnemyType.WARDEN -> Triple(rebirthProfile.enemyHealth(145f * difficulty), 29f, 1.3f)
            EnemyType.SPLITTER -> Triple(rebirthProfile.enemyHealth(118f * difficulty), 27f, 0.4f)
            EnemyType.ELITE -> Triple(rebirthProfile.enemyHealth(340f * difficulty), 38f, 0.5f)
            EnemyType.ARCHITECT -> Triple(rebirthProfile.enemyHealth(5_400f), 74f, 1.2f)
        }
        enemies += Enemy(
            id = nextEntityId++,
            type = type,
            x = x,
            y = y,
            hp = stats.first,
            maxHp = stats.first,
            radius = stats.second,
            actionTimer = stats.third,
        )
        return true
    }

    private fun resolveCollisions(delta: Float) {
        resolveEnemyCoreCollisions()
        if (phase != GamePhase.RUNNING) return
        resolveWeaponDamage(delta)
        if (phase != GamePhase.RUNNING) return
        resolveProjectileHits()
        if (phase != GamePhase.RUNNING) return
        resolvePickupCollection()
        val killed = enemies.filter { it.dead || it.hp <= 0f }
        killed.forEach(::onEnemyKilled)
        enemies.removeAll { it.dead || it.hp <= 0f }
    }

    private fun resolveEnemyCoreCollisions() {
        for (enemy in enemies) {
            if (phase != GamePhase.RUNNING) return
            val combined = CORE_RADIUS + enemy.radius
            val sweptHit = segmentCircleIntersects(
                previousCoreX - enemy.previousX,
                previousCoreY - enemy.previousY,
                coreX - enemy.x,
                coreY - enemy.y,
                0f,
                0f,
                combined,
            )
            if (enemy.contactCooldown <= 0f && sweptHit) {
                val relativeX = velocityX - enemy.vx
                val relativeY = velocityY - enemy.vy
                val impactSpeed = length(relativeX, relativeY)
                val dx = coreX - enemy.x
                val dy = coreY - enemy.y
                val distance = max(1f, length(dx, dy))
                val coreImpactSpeed = kotlin.math.abs(velocityX * dx / distance + velocityY * dy / distance)
                val damage = max(
                    6f,
                    mass * softVelocity(coreImpactSpeed) * 0.115f * damageMultiplier *
                        rebirthProfile.playerPowerMultiplier,
                )
                damageEnemy(enemy, damage, canCrit = true, relicKillProcsEligible = true)
                lastImpact = damage
                lastImpactTime = 0.72f
                enemy.contactCooldown = 0.26f
                screenShake = min(15f, 3f + softVelocity(impactSpeed) * 0.018f)
                velocityX += dx / distance * min(220f, softVelocity(impactSpeed) * 0.28f)
                velocityY += dy / distance * min(220f, softVelocity(impactSpeed) * 0.28f)
                overdriveCharge = min(125f, overdriveCharge + min(12f, coreImpactSpeed / 100f) * overdriveGain)
                shockwave(enemy.x, enemy.y, 0.26f, min(150f, 55f + damage * 0.9f), 3)
                directionalBurst(enemy.x, enemy.y, 8, 3, dx / distance, dy / distance)
                emitSound(SoundCue.IMPACT)
                if (coreImpactSpeed < 190f && dashPhaseTime <= 0f) takeDamage(7f + enemy.radius * 0.18f)
            }
        }
    }

    private fun resolveWeaponDamage(delta: Float) {
        val power = effectiveWeaponPower()
        val agonyRank = relicRank(RelicId.AGONY_SCEPTER)
        when (weapon) {
            WeaponId.FLUX_WAKE -> enemies.forEach { enemy ->
                val touching = trail.any { point ->
                    point.age < 1.8f + agonyRank * 0.18f &&
                        distanceSquared(point.x, point.y, enemy.x, enemy.y) < square(enemy.radius + 20f + agonyRank * 4f)
                }
                if (touching) {
                    dealWeaponDamage(
                        enemy,
                        (12f + softVelocity(speed) * 0.012f) * delta * power * (1f + agonyRank * 0.10f),
                        cadence = WeaponHitCadence.CONTINUOUS,
                    )
                }
            }
            WeaponId.MORNINGSTAR -> enemies.forEach { enemy ->
                val hitRadius = enemy.radius + 24f
                val primaryHit = distanceSquared(morningstarX, morningstarY, enemy.x, enemy.y) < hitRadius * hitRadius
                val mirrorX = coreX * 2f - morningstarX
                val mirrorY = coreY * 2f - morningstarY
                val forbiddenHit = agonyRank > 0 && distanceSquared(mirrorX, mirrorY, enemy.x, enemy.y) < hitRadius * hitRadius
                if (enemy.weaponCooldown <= 0f && (primaryHit || forbiddenHit)) {
                    val forbiddenScale = if (!primaryHit && forbiddenHit) 0.55f + 0.08f * agonyRank else 1f
                    dealWeaponDamage(enemy, (38f + softVelocity(speed) * 0.1f) * mass * power * forbiddenScale, canCrit = true)
                    enemy.weaponCooldown = 0.2f
                    screenShake = max(screenShake, 5f)
                }
            }
            WeaponId.PHASE_LATTICE -> enemies.forEach { enemy ->
                val ring = distanceSquared(coreX, coreY, enemy.x, enemy.y)
                if (ring in square(82f)..square(138f)) dealWeaponDamage(enemy, 19f * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
                if (agonyRank > 0 && ring in square(158f)..square(192f + agonyRank * 4f)) {
                    dealWeaponDamage(enemy, (8f + 3f * agonyRank) * delta * power, cadence = WeaponHitCadence.CONTINUOUS)
                }
            }
            WeaponId.RIFT_BLADES -> enemies.forEach { enemy ->
                if (enemy.weaponCooldown <= 0f && weaponOrbitals.any {
                        distanceSquared(it.x, it.y, enemy.x, enemy.y) <= square(it.radius + enemy.radius)
                    }
                ) {
                    dealWeaponDamage(enemy, (31f + softVelocity(speed) * 0.025f) * power, canCrit = true)
                    enemy.weaponCooldown = 0.17f
                }
            }
            WeaponId.ENTROPY_FIELD -> enemies.forEach { enemy ->
                val radius = 170f + weaponLevel * 5f + agonyRank * 18f
                if (distanceSquared(coreX, coreY, enemy.x, enemy.y) <= square(radius + enemy.radius)) {
                    val collapse = agonyRank > 0 && enemy.hp <= enemy.maxHp * (0.06f + agonyRank * 0.015f)
                    dealWeaponDamage(
                        enemy,
                        if (collapse) enemy.hp else 24f * delta * power * (1f + agonyRank * 0.08f),
                        cadence = WeaponHitCadence.CONTINUOUS,
                    )
                    enemy.vx *= exp(-0.5f * delta)
                    enemy.vy *= exp(-0.5f * delta)
                }
            }
            else -> Unit
        }
    }

    private fun resolveProjectileHits() {
        val hostileIterator = projectiles.iterator()
        while (hostileIterator.hasNext()) {
            if (phase != GamePhase.RUNNING) break
            val projectile = hostileIterator.next()
            if (!projectile.hostile) continue
            val hitRadius = CORE_RADIUS + projectile.radius
            val hit = segmentCircleIntersects(
                projectile.previousX - previousCoreX,
                projectile.previousY - previousCoreY,
                projectile.x - coreX,
                projectile.y - coreY,
                0f,
                0f,
                hitRadius,
            )
            if (hit) {
                hostileIterator.remove()
                if (dashPhaseTime <= 0f) {
                    takeDamage(12f)
                    screenShake = max(screenShake, 6f)
                } else {
                    grantMatter(1f)
                    burst(projectile.x, projectile.y, 5, 2)
                }
            }
        }

        val friendlyIterator = projectiles.iterator()
        while (friendlyIterator.hasNext()) {
            val projectile = friendlyIterator.next()
            if (projectile.hostile) continue
            var consumed = false
            for (enemy in enemies) {
                if (enemy.id in projectile.hitEnemyIds || enemy.dead) continue
                val hitRadius = projectile.radius + enemy.radius
                val hit = segmentCircleIntersects(
                    projectile.previousX - enemy.previousX,
                    projectile.previousY - enemy.previousY,
                    projectile.x - enemy.x,
                    projectile.y - enemy.y,
                    0f,
                    0f,
                    hitRadius,
                )
                if (hit) {
                    projectile.hitEnemyIds += enemy.id
                    val sourceWeapon = projectile.sourceWeapon
                    if (sourceWeapon != null) {
                        dealWeaponDamage(enemy, projectile.damage, canCrit = true, sourceWeapon = sourceWeapon)
                    } else {
                        damageEnemy(enemy, projectile.damage, canCrit = true)
                    }
                    burst(enemy.x, enemy.y, 4, projectile.colorIndex)
                    projectile.pierce--
                    if (projectile.pierce < 0) {
                        consumed = true
                        break
                    }
                    if (projectile.sourceWeapon == WeaponId.PRISM_RELAY) {
                        redirectPrismRelay(projectile)
                        break
                    }
                }
            }
            if (consumed) friendlyIterator.remove()
        }
    }

    private fun redirectPrismRelay(projectile: Projectile) {
        val target = enemies.asSequence()
            .filter { !it.dead && it.id !in projectile.hitEnemyIds }
            .filter { distanceSquared(projectile.x, projectile.y, it.x, it.y) <= square(720f) }
            .minByOrNull { distanceSquared(projectile.x, projectile.y, it.x, it.y) }
            ?: return
        val angle = atan2(target.y - projectile.y, target.x - projectile.x)
        val projectileSpeed = max(520f, length(projectile.vx, projectile.vy))
        projectile.vx = cos(angle) * projectileSpeed
        projectile.vy = sin(angle) * projectileSpeed
        projectile.previousX = projectile.x
        projectile.previousY = projectile.y
        projectile.life = max(projectile.life, 0.9f)
        addWeaponArc(projectile.x, projectile.y, target.x, target.y, 0.08f)
    }

    private fun resolvePickupCollection() {
        val iterator = pickups.iterator()
        while (iterator.hasNext()) {
            val pickup = iterator.next()
            if (segmentCircleIntersects(
                    previousCoreX - pickup.previousX,
                    previousCoreY - pickup.previousY,
                    coreX - pickup.x,
                    coreY - pickup.y,
                    0f,
                    0f,
                    34f,
                )
            ) {
                iterator.remove()
                when (pickup.type) {
                    PickupType.DATA -> gainData(dataGain)
                    PickupType.KEY -> {
                        keys++
                        message = "ELITE KEY ACQUIRED"
                        messageTime = 1.4f
                    }
                    PickupType.REPAIR -> hp = min(maxHp, hp + 22f)
                    PickupType.RELIC -> {
                        pendingRelicChoices++
                        message = "RELIC RESONANCE"
                        messageTime = 1.4f
                    }
                }
                burst(pickup.x, pickup.y, 7, if (pickup.type == PickupType.KEY || pickup.type == PickupType.RELIC) 3 else 1)
                emitSound(SoundCue.PICKUP)
                openNextPendingChoice()
            }
        }
    }

    private fun gainData(amount: Float) {
        dataFraction += amount
        val whole = floor(dataFraction).toInt()
        if (whole <= 0) return
        dataFraction -= whole
        data += whole
        while (data >= nextLevelData) {
            data -= nextLevelData
            level++
            pendingLevelChoices++
            nextLevelData = 18 + level * 8 + (level * level) / 8
        }
        openNextPendingChoice()
    }

    private fun damageEnemy(
        enemy: Enemy,
        baseAmount: Float,
        canCrit: Boolean = false,
        bonusCritChance: Float = 0f,
        bonusCritDamage: Float = 0f,
        relicKillProcsEligible: Boolean = false,
    ): DamageResult {
        if (baseAmount <= 0f || enemy.dead || enemy.hp <= 0f) return DamageResult(0f, false)
        val effectiveCritChance = (critChance + bonusCritChance).coerceIn(0f, 0.75f)
        val effectiveCritDamage = critMultiplier + bonusCritDamage
        val critical = canCrit && gameplayRandom.nextFloat() < effectiveCritChance
        val amount = when {
            critical -> baseAmount * effectiveCritDamage
            !canCrit -> baseAmount * (1f + effectiveCritChance * (effectiveCritDamage - 1f))
            else -> baseAmount
        }
        enemy.hp -= amount
        if (enemy.hp <= 0f) enemy.relicKillProcsEligible = relicKillProcsEligible
        enemy.flash = max(enemy.flash, if (amount >= 5f) 1f else 0.16f)
        if (settings.damageNumbers && amount >= 5f) {
            emitVisualFx(
                VisualFxCue.DamageNumberAdded(
                    x = enemy.x,
                    y = enemy.y - enemy.radius,
                    amount = amount.roundToLong(),
                    critical = critical,
                ),
            )
        }
        return DamageResult(amount, critical)
    }

    private fun dealWeaponDamage(
        enemy: Enemy,
        baseAmount: Float,
        canCrit: Boolean = false,
        cadence: WeaponHitCadence = WeaponHitCadence.DISCRETE,
        sourceWeapon: WeaponId = weapon,
    ): DamageResult {
        if (baseAmount <= 0f || enemy.dead || enemy.hp <= 0f) return DamageResult(0f, false)
        val qualified = cadence == WeaponHitCadence.DISCRETE || enemy.relicQualificationCooldown <= 0f
        var multiplier = 1f

        val flywheelRank = relicRank(RelicId.KINETIC_FLYWHEEL)
        if (flywheelRank > 0 && speed >= 500f) {
            multiplier += 0.05f * flywheelRank * if (speed >= 1_600f) 2f else 1f
        }
        val overtakeRank = relicRank(RelicId.OVERTAKE_PROTOCOL)
        if (overtakeRank > 0 && length(enemy.vx, enemy.vy) >= 170f) multiplier += 0.07f * overtakeRank
        if (qualified && brakepointCharge > 0f) multiplier += brakepointCharge
        val polarityRank = relicRank(RelicId.POLARITY_SLING)
        if (polarityRank > 0) multiplier += 0.08f * polarityRank * (1f - polarityStability)
        val distanceFromCore = length(enemy.x - coreX, enemy.y - coreY)
        val periapsisRank = relicRank(RelicId.PERIAPSIS_HOOK)
        if (periapsisRank > 0 && distanceFromCore > 300f) multiplier += 0.08f * periapsisRank
        val crushRank = relicRank(RelicId.CRUSH_DEPTH)
        if (crushRank > 0 && distanceFromCore <= 155f) multiplier += 0.06f * crushRank
        val massRank = relicRank(RelicId.MASS_ECHO)
        if (massRank > 0) multiplier += 0.04f * massRank * mass
        val tidalRank = relicRank(RelicId.TIDAL_LOCK)
        if (tidalRank > 0) {
            multiplier += 0.02f * tidalRank * enemy.relicCounters[RelicId.TIDAL_LOCK.ordinal].coerceIn(0, 5)
        }
        val returnRank = relicRank(RelicId.RETURN_CIRCUIT)
        if (returnRank > 0 && isRelicTargetIsolated(enemy, 260f)) multiplier += 0.09f * returnRank
        val glassRank = relicRank(RelicId.GLASS_WITNESS)
        if (glassRank > 0 && enemy.relicTimers[RelicId.GLASS_WITNESS.ordinal] > 0f) multiplier += 0.07f * glassRank
        val doomRank = relicRank(RelicId.DOOM_CLOCK)
        if (doomRank > 0 && (enemy.type == EnemyType.ELITE || enemy.type == EnemyType.ARCHITECT)) {
            multiplier += 0.08f * doomRank * (1f + runProgress)
        }
        val lastLightRank = relicRank(RelicId.LAST_LIGHT)
        if (lastLightRank > 0 && hp <= maxHp * 0.35f) multiplier += 0.12f * lastLightRank
        val crownRank = relicRank(RelicId.CROWN_OF_FOUR_WINDS)
        if (crownRank > 0) multiplier += 0.04f * crownRank * distinctRelicAspectCount()

        var amount = baseAmount * multiplier
        val circuitRank = relicRank(RelicId.CIRCUIT_BREAKER)
        if (qualified && circuitRank > 0 && enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] == 0) {
            amount += 11f * circuitRank
        }
        val fractureLensRank = relicRank(RelicId.FRACTURE_LENS)
        val bonusCritChance = if (fractureLensRank > 0 && enemy.hp < enemy.maxHp) 0.025f * fractureLensRank else 0f
        val hardlightRank = relicRank(RelicId.HARDLIGHT_EDGE)
        val result = damageEnemy(
            enemy,
            amount,
            canCrit,
            bonusCritChance,
            0.12f * hardlightRank,
            relicKillProcsEligible = true,
        )
        if (qualified && result.amount > 0f) {
            if (cadence == WeaponHitCadence.CONTINUOUS) enemy.relicQualificationCooldown = 0.22f
            if (brakepointCharge > 0f) brakepointCharge = 0f
            onQualifiedWeaponHit(enemy, result, sourceWeapon)
        }
        return result
    }

    private fun onQualifiedWeaponHit(enemy: Enemy, result: DamageResult, sourceWeapon: WeaponId) {
        val orbitalRank = relicRank(RelicId.ORBITAL_NAIL)
        if (orbitalRank > 0) {
            val dx = coreX - enemy.x
            val dy = coreY - enemy.y
            val distance = max(1f, length(dx, dy))
            enemy.vx += dx / distance * 24f * orbitalRank
            enemy.vy += dy / distance * 24f * orbitalRank
            relicProcCounts[RelicId.ORBITAL_NAIL.ordinal]++
        }

        val massRank = relicRank(RelicId.MASS_ECHO)
        if (massRank > 0) {
            val dx = enemy.x - coreX
            val dy = enemy.y - coreY
            val distance = max(1f, length(dx, dy))
            val recoil = 18f * massRank * mass
            enemy.vx += dx / distance * recoil
            enemy.vy += dy / distance * recoil
            relicProcCounts[RelicId.MASS_ECHO.ordinal]++
        }

        val tidalRank = relicRank(RelicId.TIDAL_LOCK)
        if (tidalRank > 0) {
            val index = RelicId.TIDAL_LOCK.ordinal
            enemy.relicCounters[index] = min(5, enemy.relicCounters[index] + 1)
        }

        val filamentRank = relicRank(RelicId.VOLTAIC_FILAMENT)
        if (filamentRank > 0 && relicCooldowns[RelicId.VOLTAIC_FILAMENT.ordinal] <= 0f) {
            nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 440f)?.let { target ->
                damageEnemy(target, result.amount * 0.16f * filamentRank)
                addRelicArc(enemy.x, enemy.y, target.x, target.y)
                relicCooldowns[RelicId.VOLTAIC_FILAMENT.ordinal] = 0.28f
                relicProcCounts[RelicId.VOLTAIC_FILAMENT.ordinal]++
            }
        }

        val staticRank = relicRank(RelicId.STATIC_CHORUS)
        if (staticRank > 0) {
            val index = RelicId.STATIC_CHORUS.ordinal
            relicCounters[index]++
            if (relicCounters[index] % 7 == 0) {
                chainRelicDamage(enemy, staticRank, 520f, result.amount * 0.12f)
                relicProcCounts[index]++
            }
        }

        val debtRank = relicRank(RelicId.ION_DEBT)
        if (debtRank > 0) {
            val index = RelicId.ION_DEBT.ordinal
            enemy.relicCounters[index]++
            if (enemy.relicCounters[index] >= 5) {
                enemy.relicCounters[index] = 0
                areaRelicDamage(enemy.x, enemy.y, 95f + 10f * debtRank, 14f * debtRank, enemy.id)
                relicProcCounts[index]++
            }
        }

        val circuitRank = relicRank(RelicId.CIRCUIT_BREAKER)
        if (circuitRank > 0 && enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] == 0) {
            enemy.relicCounters[RelicId.CIRCUIT_BREAKER.ordinal] = 1
            val interruption = (1f - 0.08f * circuitRank).coerceAtLeast(0.35f)
            enemy.vx *= interruption
            enemy.vy *= interruption
            relicProcCounts[RelicId.CIRCUIT_BREAKER.ordinal]++
        }

        val stormRank = relicRank(RelicId.STORM_INDEX)
        if (stormRank > 0 && velocityTier >= 1) {
            val index = RelicId.STORM_INDEX.ordinal
            relicCounters[index]++
            if (relicCounters[index] % 4 == 0) {
                nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 220f + 45f * stormRank)?.let { target ->
                    damageEnemy(target, 10f * stormRank)
                    addRelicArc(enemy.x, enemy.y, target.x, target.y)
                    relicProcCounts[index]++
                }
            }
        }

        val echoRank = relicRank(RelicId.ECHO_CHAMBER)
        if (echoRank > 0 && delayedRelicHits.size < MAX_DELAYED_RELIC_HITS) {
            delayedRelicHits += DelayedRelicHit(RelicId.ECHO_CHAMBER, enemy.id, 0.45f, result.amount * 0.12f * echoRank)
        }
        val palimpsestRank = relicRank(RelicId.PALIMPSEST_ROUND)
        if (palimpsestRank > 0) {
            val index = RelicId.PALIMPSEST_ROUND.ordinal
            relicCounters[index]++
            if (relicCounters[index] % 7 == 0) {
                damageEnemy(enemy, result.amount * 0.20f * palimpsestRank)
                relicProcCounts[index]++
            }
        }
        val fractureRank = relicRank(RelicId.FRACTURE_GATE)
        if (fractureRank > 0) {
            val index = RelicId.FRACTURE_GATE.ordinal
            enemy.relicCounters[index]++
            if (enemy.relicCounters[index] % 6 == 0 && enemy.hp > 0f) {
                enemy.x = coreX - (enemy.x - coreX)
                enemy.y = coreY - (enemy.y - coreY)
                enemy.previousX = enemy.x
                enemy.previousY = enemy.y
                damageEnemy(enemy, 12f * fractureRank)
                shockwave(enemy.x, enemy.y, 0.2f, 52f + 8f * fractureRank, 2)
                relicProcCounts[index]++
            }
        }

        val glassRank = relicRank(RelicId.GLASS_WITNESS)
        if (glassRank > 0 && enemy.relicCounters[RelicId.GLASS_WITNESS.ordinal] == 0) {
            enemy.relicCounters[RelicId.GLASS_WITNESS.ordinal] = 1
            enemy.relicTimers[RelicId.GLASS_WITNESS.ordinal] = 3f
        }
        val spectralRank = relicRank(RelicId.SPECTRAL_FAN)
        if (spectralRank > 0) {
            val index = RelicId.SPECTRAL_FAN.ordinal
            relicCounters[index]++
            if (relicCounters[index] % 6 == 0) {
                chainRelicDamage(enemy, 2, 500f, result.amount * 0.14f * spectralRank)
                relicProcCounts[index]++
            }
        }
        val chromaRank = relicRank(RelicId.CHROMA_FEEDBACK)
        if (chromaRank > 0 && enemy.relicCounters[RelicId.CHROMA_FEEDBACK.ordinal] == 0) {
            enemy.relicCounters[RelicId.CHROMA_FEEDBACK.ordinal] = 1
            if (maxShield > 0f && shield < maxShield) {
                shield = min(maxShield, shield + 1.5f * chromaRank)
            } else {
                overdriveCharge = min(125f, overdriveCharge + 2f * chromaRank)
            }
            relicProcCounts[RelicId.CHROMA_FEEDBACK.ordinal]++
        }
        val mirrorCutRank = relicRank(RelicId.MIRROR_CUT)
        if (mirrorCutRank > 0 && result.critical) {
            nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 560f)?.let { target ->
                damageEnemy(target, result.amount * 0.15f * mirrorCutRank)
                addRelicArc(enemy.x, enemy.y, target.x, target.y)
                relicProcCounts[RelicId.MIRROR_CUT.ordinal]++
            }
        }

        val heatRank = relicRank(RelicId.HEAT_DEBT)
        if (heatRank > 0) {
            val index = RelicId.HEAT_DEBT.ordinal
            enemy.relicCounters[index]++
            enemy.relicValues[index] += result.amount * 0.08f * heatRank
            if (enemy.relicCounters[index] >= 5) {
                enemy.relicCounters[index] = 0
                val storedDamage = enemy.relicValues[index]
                enemy.relicValues[index] = 0f
                areaRelicDamage(enemy.x, enemy.y, 105f + 8f * heatRank, 16f * heatRank + storedDamage)
                relicProcCounts[index]++
            }
        }
        val scarRank = relicRank(RelicId.SCAR_TISSUE)
        if (scarRank > 0) {
            val index = RelicId.SCAR_TISSUE.ordinal
            enemy.relicCounters[index] = min(5, enemy.relicCounters[index] + 1)
            enemy.relicTimers[index] = 3f
        }

        val huntRank = relicRank(RelicId.MIRROR_OF_THE_HUNT)
        if (huntRank > 0 && enemy.relicCounters[RelicId.MIRROR_OF_THE_HUNT.ordinal] == 0) {
            enemy.relicCounters[RelicId.MIRROR_OF_THE_HUNT.ordinal] = 1
            nearestOtherEnemy(coreX, coreY, enemy.id, 720f)?.let { target ->
                damageEnemy(target, result.amount * 0.20f * huntRank)
                addRelicArc(enemy.x, enemy.y, target.x, target.y)
                relicProcCounts[RelicId.MIRROR_OF_THE_HUNT.ordinal]++
            }
        }

        if (sourceWeapon == WeaponId.PRISM_RELAY && relicRank(RelicId.AGONY_SCEPTER) > 0) {
            // Prism's Scepter mutation is applied at launch; retaining source identity here prevents accidental proc recursion.
        }
    }

    private fun isRelicTargetIsolated(enemy: Enemy, range: Float): Boolean = enemies.none {
        it.id != enemy.id && !it.dead && it.hp > 0f && distanceSquared(enemy.x, enemy.y, it.x, it.y) <= range * range
    }

    private fun nearestOtherEnemy(x: Float, y: Float, excludedId: Int, range: Float): Enemy? = enemies
        .asSequence()
        .filter { it.id != excludedId && !it.dead && it.hp > 0f && distanceSquared(x, y, it.x, it.y) <= range * range }
        .minByOrNull { distanceSquared(x, y, it.x, it.y) }

    private fun chainRelicDamage(origin: Enemy, count: Int, range: Float, damage: Float) {
        val used = mutableSetOf(origin.id)
        var fromX = origin.x
        var fromY = origin.y
        repeat(count) {
            val target = enemies.asSequence()
                .filter { it.id !in used && !it.dead && it.hp > 0f && distanceSquared(fromX, fromY, it.x, it.y) <= range * range }
                .minByOrNull { distanceSquared(fromX, fromY, it.x, it.y) }
                ?: return@repeat
            used += target.id
            damageEnemy(target, damage)
            addRelicArc(fromX, fromY, target.x, target.y)
            fromX = target.x
            fromY = target.y
        }
    }

    private fun areaRelicDamage(x: Float, y: Float, radius: Float, damage: Float, excludedId: Int = -1) {
        enemies.forEach { target ->
            if (target.id != excludedId && !target.dead && target.hp > 0f &&
                distanceSquared(x, y, target.x, target.y) <= square(radius + target.radius)
            ) {
                damageEnemy(target, damage)
            }
        }
        shockwave(x, y, 0.24f, radius, 2)
    }

    private fun addRelicArc(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        addWeaponArc(fromX, fromY, toX, toY, 0.12f)
    }

    private fun distinctRelicAspectCount(): Int {
        var count = 0
        for (index in equippedRelics.indices) {
            val relic = equippedRelics[index]
            val aspect = RelicCatalog.byId(relic.id).aspect
            var seen = false
            for (prior in 0 until index) {
                if (RelicCatalog.byId(equippedRelics[prior].id).aspect == aspect) {
                    seen = true
                    break
                }
            }
            if (aspect != RelicAspect.SOVEREIGN && !seen) {
                count++
            }
        }
        return count
    }

    private fun takeDamage(rawAmount: Float) {
        if (hurtCooldown > 0f) return
        var amount = rawAmount * rebirthProfile.incomingDamageMultiplier *
            (1f - damageReduction.coerceIn(0f, 0.65f))
        if (shield > 0f) {
            val absorbed = min(shield, amount)
            shield -= absorbed
            amount -= absorbed
        }
        if (amount > 0f) hp -= amount
        if (amount > 0f && relicRank(RelicId.BORROWED_MOMENT) > 0) {
            borrowedMomentTime = 2.5f
            relicProcCounts[RelicId.BORROWED_MOMENT.ordinal]++
        }
        timeSinceDamage = 0f
        shieldRechargeDelay = 4f
        hurtCooldown = 0.14f
        damageFlash = 1f
        shockwave(coreX, coreY, 0.3f, 90f, 4)
        burst(coreX, coreY, 8, 4)
        emitSound(SoundCue.HURT)
        if (hp <= 0f) endRun("CORE FRACTURED")
    }

    private fun onEnemyKilled(enemy: Enemy) {
        if (enemy.dead) return
        enemy.dead = true
        kills++
        combo++
        comboTime = comboWindow
        if (enemy.relicKillProcsEligible) triggerRelicKillEffects(enemy)
        val baseMatter = when (enemy.type) {
            EnemyType.ELITE -> 4f
            EnemyType.ARCHITECT -> 30f
            else -> 1f
        }
        val comboMultiplier = 1f + min(combo, 50) * 0.01f + velocityTier * 0.15f
        grantMatter(baseMatter * comboMultiplier)
        val dataCount = when (enemy.type) {
            EnemyType.DRIFTER -> 1
            EnemyType.SHOOTER -> 2
            EnemyType.CHARGER -> 3
            EnemyType.INTERCEPTOR -> 3
            EnemyType.WEAVER -> 3
            EnemyType.WARDEN -> 5
            EnemyType.SPLITTER -> 4
            EnemyType.ELITE -> 8
            EnemyType.ARCHITECT -> 20
        }
        overdriveCharge = min(125f, overdriveCharge + dataCount * 1.8f * overdriveGain)
        repeat(dataCount) {
            val angle = gameplayRandom.nextFloat() * TAU
            val pickupSpeed = 28f + gameplayRandom.nextFloat() * 70f
            addPickup(Pickup(PickupType.DATA, enemy.x, enemy.y, cos(angle) * pickupSpeed, sin(angle) * pickupSpeed))
        }
        if (enemy.type == EnemyType.ELITE) {
            addPickup(Pickup(PickupType.KEY, enemy.x, enemy.y))
            addPickup(Pickup(PickupType.RELIC, enemy.x + 15f, enemy.y - 8f))
        }
        if (enemy.type != EnemyType.ARCHITECT && gameplayRandom.nextFloat() < 0.035f + luck * 0.02f) {
            addPickup(Pickup(PickupType.REPAIR, enemy.x, enemy.y))
        }
        if (enemy.type == EnemyType.SPLITTER) spawnSplitterFragments(enemy)
        shockwave(
            enemy.x,
            enemy.y,
            if (enemy.type == EnemyType.ARCHITECT) 0.8f else 0.3f,
            enemy.radius * if (enemy.type == EnemyType.ARCHITECT) 4.5f else 2.7f,
            if (enemy.type == EnemyType.ELITE) 3 else 1,
        )
        burst(enemy.x, enemy.y, if (enemy.type == EnemyType.ARCHITECT) 60 else 12, if (enemy.type == EnemyType.ELITE) 3 else 1)
        emitSound(SoundCue.ENEMY_DESTROYED)
        if (enemy.type == EnemyType.ARCHITECT) {
            phase = GamePhase.VICTORY
            message = "ARCHITECT DISMANTLED"
            messageTime = 10f
            if (!externalAuthorities) {
                highestClearedRebirth = max(highestClearedRebirth, rebirthLevel)
            }
            bankRunMatter()
            emitSound(SoundCue.VICTORY)
        }
    }

    private fun triggerRelicKillEffects(enemy: Enemy) {
        val slipstreamRank = relicRank(RelicId.SLIPSTREAM_RELAY)
        if (slipstreamRank > 0 && speed >= 500f) {
            slipstreamRelayTime = 3f
            relicProcCounts[RelicId.SLIPSTREAM_RELAY.ordinal]++
        }

        val eventideRank = relicRank(RelicId.EVENTIDE_ANCHOR)
        if (eventideRank > 0) {
            val radius = 72f + 18f * eventideRank
            enemies.forEach { target ->
                if (!target.dead && target.hp > 0f && target.id != enemy.id) {
                    val dx = enemy.x - target.x
                    val dy = enemy.y - target.y
                    val distance = max(1f, length(dx, dy))
                    if (distance <= radius + target.radius) {
                        damageEnemy(target, 18f * eventideRank)
                        target.vx += dx / distance * 55f * eventideRank
                        target.vy += dy / distance * 55f * eventideRank
                    }
                }
            }
            shockwave(enemy.x, enemy.y, 0.28f, radius, 2)
            relicProcCounts[RelicId.EVENTIDE_ANCHOR.ordinal]++
        }

        val secondHandRank = relicRank(RelicId.SECOND_HAND)
        if (secondHandRank > 0) {
            weaponClock -= 0.08f * secondHandRank
            weaponSecondaryClock -= 0.08f * secondHandRank
            relicProcCounts[RelicId.SECOND_HAND.ordinal]++
        }

        val splitRank = relicRank(RelicId.SPLIT_HORIZON)
        if (splitRank > 0) {
            nearestOtherEnemy(enemy.x, enemy.y, enemy.id, 760f)?.let { target ->
                fireRelicProjectile(enemy.x, enemy.y, target, 18f * splitRank)
                relicProcCounts[RelicId.SPLIT_HORIZON.ordinal]++
            }
        }

        val quietusRank = relicRank(RelicId.QUIETUS_BLOOM)
        if (quietusRank > 0) {
            val radius = 68f + 16f * quietusRank
            val slow = (1f - 0.10f * quietusRank).coerceAtLeast(0.35f)
            enemies.forEach { target ->
                if (!target.dead && target.hp > 0f && target.id != enemy.id &&
                    distanceSquared(enemy.x, enemy.y, target.x, target.y) <= square(radius + target.radius)
                ) {
                    damageEnemy(target, 10f * quietusRank)
                    target.vx *= slow
                    target.vy *= slow
                }
            }
            shockwave(enemy.x, enemy.y, 0.34f, radius, 4)
            relicProcCounts[RelicId.QUIETUS_BLOOM.ordinal]++
        }

        val tollRank = relicRank(RelicId.DEVOURERS_TOLL)
        if (tollRank > 0) {
            val index = RelicId.DEVOURERS_TOLL.ordinal
            relicCounters[index]++
            if (relicCounters[index] % 3 == 0) {
                hp = min(maxHp, hp + 1.5f * tollRank)
                shield = min(maxShield, shield + 1.5f * tollRank)
                relicProcCounts[index]++
            }
        }
    }

    private fun fireRelicProjectile(x: Float, y: Float, target: Enemy, damage: Float) {
        val angle = atan2(target.y - y, target.x - x)
        addProjectile(Projectile(
            x = x,
            y = y,
            vx = cos(angle) * 720f,
            vy = sin(angle) * 720f,
            radius = 5f,
            life = 2f,
            hostile = false,
            damage = damage,
            pierce = 0,
            colorIndex = 2,
            sourceWeapon = null,
        ))
    }

    private fun spawnSplitterFragments(enemy: Enemy) {
        val difficulty = 1f + elapsed / 470f
        val fragmentLimit = rebirthProfile.enemyCap(90)
        val fragmentCount = min(2, max(0, fragmentLimit - enemies.count { !it.dead }))
        repeat(fragmentCount) { index ->
            if (enemies.size >= MAX_ENEMIES) return@repeat
            val angle = gameplayRandom.nextFloat() * TAU + index * TAU * 0.5f
            val fragmentHp = rebirthProfile.enemyHealth(24f * difficulty)
            val fragmentSpeed = 175f * rebirthProfile.enemySpeedMultiplier
            enemies += Enemy(
                id = nextEntityId++,
                type = EnemyType.DRIFTER,
                x = enemy.x + cos(angle) * 13f,
                y = enemy.y + sin(angle) * 13f,
                vx = cos(angle) * fragmentSpeed,
                vy = sin(angle) * fragmentSpeed,
                hp = fragmentHp,
                maxHp = fragmentHp,
                radius = 12f,
                actionTimer = 0.2f,
            )
        }
    }

    private fun openItemChoice() {
        activeChoiceType = ChoiceType.ITEM
        buildItemChoices()
        dashBufferTime = 0f
        phase = GamePhase.CHOICE
    }

    private fun buildItemChoices() {
        val lifetimeUnlock = (1L + lifetimeMatter / 40L).coerceAtMost(80L).toInt()
        val catalogLevel = max(level, lifetimeUnlock)
        val unlocked = ItemCatalog.all.filter { it.unlockLevel <= catalogLevel }
        val eligible = unlocked.filter { itemStacks[it.id] < it.maxStacks }
        val selected = mutableListOf<ItemDefinition>()
        repeat(3) {
            val preferred = eligible.filter { candidate -> selected.none { it.id == candidate.id } }
            val available = preferred.ifEmpty { unlocked.filter { candidate -> selected.none { it.id == candidate.id } } }
            if (available.isEmpty()) return@repeat
            val rarity = rollRarity()
            val rarityPool = available.filter { it.rarity == rarity }.ifEmpty { available }
            selected += rarityPool[gameplayRandom.nextInt(rarityPool.size)]
        }
        choices = selected.map {
            ChoiceOption(
                type = ChoiceType.ITEM,
                title = it.name,
                description = compactItemDescription(it),
                tag = it.rarity.displayLabel.uppercase(),
                itemId = it.id,
            )
        }
    }

    private fun rollRarity(): ItemRarity {
        val roll = gameplayRandom.nextFloat() - luck.coerceIn(0f, 2f) * 0.08f
        return when {
            roll < 0.03f -> ItemRarity.LEGENDARY
            roll < 0.13f -> ItemRarity.EPIC
            roll < 0.34f -> ItemRarity.RARE
            roll < 0.67f -> ItemRarity.UNCOMMON
            else -> ItemRarity.COMMON
        }
    }

    private fun openTotemChoice() {
        activeChoiceType = ChoiceType.TOTEM
        val nextLevel = weaponLevel + 1
        val nextMastery = WeaponMastery.forLevel(nextLevel)
        val masteryTag = if (nextMastery != currentWeaponMastery) {
            nextMastery.displayLabel.uppercase()
        } else {
            "LEVEL $nextLevel"
        }
        choices = listOf(
            ChoiceOption(
                type = ChoiceType.TOTEM,
                title = "Amplify ${currentWeaponDefinition.name}",
                description = "Advance the current system from level $weaponLevel to $nextLevel immediately.",
                tag = masteryTag,
                weaponId = weapon,
                totemAction = TotemAction.AMPLIFY_CURRENT,
            ),
            ChoiceOption(
                type = ChoiceType.TOTEM,
                title = "Change weapon",
                description = "Recalibrate the Totem, then choose one of three different weapon systems.",
                tag = "RECALIBRATE",
                totemAction = TotemAction.CHANGE_WEAPON,
            ),
        )
        dashBufferTime = 0f
        phase = GamePhase.CHOICE
    }

    private fun openWeaponChoice() {
        activeChoiceType = ChoiceType.WEAPON
        buildWeaponChoices()
        dashBufferTime = 0f
        phase = GamePhase.CHOICE
    }

    private fun buildWeaponChoices() {
        val pool = WeaponCatalog.all.filter { it.id != weapon }.shuffled(gameplayRandom).take(3)
        choices = pool.map {
            ChoiceOption(
                type = ChoiceType.WEAPON,
                title = it.name,
                description = it.description,
                tag = it.tags.first(),
                weaponId = it.id,
            )
        }
    }

    private fun openRelicChoice() {
        activeChoiceType = ChoiceType.RELIC
        buildRelicChoices()
        dashBufferTime = 0f
        phase = GamePhase.CHOICE
    }

    private fun buildRelicChoices() {
        val selected = mutableListOf<RelicDefinition>()
        repeat(3) {
            val excluded = selected.mapTo(mutableSetOf()) { it.id }
            val sovereignChance = (0.08f + luck.coerceIn(0f, 2f) * 0.01f).coerceAtMost(0.10f)
            val preferredAspect = if (gameplayRandom.nextFloat() < sovereignChance) {
                RelicAspect.SOVEREIGN
            } else {
                RelicAspect.entries[gameplayRandom.nextInt(RelicAspect.entries.size - 1)]
            }
            val preferred = RelicCatalog.all.filter { it.aspect == preferredAspect && it.id !in excluded }
            val available = preferred.ifEmpty { RelicCatalog.all.filter { it.id !in excluded } }
            selected += available[gameplayRandom.nextInt(available.size)]
        }
        choices = buildList {
            selected.forEach { relic ->
                val currentRank = relicRank(relic.id)
                add(
                    ChoiceOption(
                        type = ChoiceType.RELIC,
                        title = relic.name,
                        description = if (currentRank > 0) {
                            if (currentRank < RelicCatalog.MAX_RANK) {
                                "Merge the duplicate resonance and advance rank $currentRank to ${currentRank + 1}."
                            } else {
                                "This resonance is already rank ${RelicCatalog.MAX_RANK}; selecting it salvages Kinetic Matter."
                            }
                        } else {
                            relic.description
                        },
                        tag = relic.aspect.displayLabel.uppercase(),
                        relicId = relic.id,
                        relicAction = RelicChoiceAction.ACQUIRE,
                    ),
                )
            }
            if (equippedRelics.size >= RelicCatalog.MAX_SLOTS) {
                add(
                    ChoiceOption(
                        type = ChoiceType.RELIC,
                        title = "Meld resonance",
                        description = "Collapse this offering into one of the four bound Relics and raise its rank.",
                        tag = "MELD",
                        relicAction = RelicChoiceAction.MELD,
                    ),
                )
            }
        }
    }

    private fun openRelicBindChoice() {
        activeChoiceType = ChoiceType.RELIC_BIND
        val action = pendingRelicBindAction ?: return
        val incoming = pendingBindingRelic
        choices = equippedRelics.mapIndexed { index, equipped ->
            val current = RelicCatalog.byId(equipped.id)
            when (action) {
                RelicChoiceAction.REPLACE -> {
                    val replacement = RelicCatalog.byId(incoming ?: return)
                    ChoiceOption(
                        type = ChoiceType.RELIC_BIND,
                        title = "Replace ${current.name}",
                        description = "Break slot ${index + 1} and bind ${replacement.name} at rank 1.",
                        tag = "SLOT ${index + 1} // REPLACE",
                        relicId = replacement.id,
                        relicAction = RelicChoiceAction.REPLACE,
                        relicSlot = index,
                    )
                }
                RelicChoiceAction.MELD_TARGET -> ChoiceOption(
                    type = ChoiceType.RELIC_BIND,
                    title = "Meld ${current.name}",
                    description = if (equipped.rank < RelicCatalog.MAX_RANK) {
                        "Collapse the offering into slot ${index + 1} and advance rank ${equipped.rank} to ${equipped.rank + 1}."
                    } else {
                        "Slot ${index + 1} is already rank ${RelicCatalog.MAX_RANK}; salvage the excess resonance."
                    },
                    tag = "SLOT ${index + 1} // RANK ${equipped.rank}",
                    relicId = equipped.id,
                    relicAction = RelicChoiceAction.MELD_TARGET,
                    relicSlot = index,
                )
                RelicChoiceAction.ACQUIRE, RelicChoiceAction.MELD -> return
            }
        }
        dashBufferTime = 0f
        phase = GamePhase.CHOICE
    }

    private fun acquireRelic(id: RelicId) {
        val currentIndex = equippedRelics.indexOfFirst { it.id == id }
        if (currentIndex >= 0) {
            val current = equippedRelics[currentIndex]
            if (current.rank >= RelicCatalog.MAX_RANK) {
                grantMatter(8f)
                message = RelicCatalog.byId(id).name.uppercase() + " // RESONANCE SALVAGED"
            } else {
                val updated = equippedRelics.toMutableList()
                updated[currentIndex] = current.copy(rank = current.rank + 1)
                equippedRelics = updated.toList()
                relicRanks[id.ordinal] = current.rank + 1
                message = RelicCatalog.byId(id).name.uppercase() + " // RANK " + (current.rank + 1)
            }
            messageTime = 1.7f
            return
        }
        if (equippedRelics.size >= RelicCatalog.MAX_SLOTS) return
        equippedRelics = equippedRelics + EquippedRelic(id, 1)
        relicRanks[id.ordinal] = 1
        message = RelicCatalog.byId(id).name.uppercase() + " // BOUND"
        messageTime = 1.7f
    }

    private fun replaceRelic(slot: Int, id: RelicId) {
        if (slot !in equippedRelics.indices) return
        val replaced = equippedRelics[slot]
        clearRelicRuntime(replaced.id)
        val updated = equippedRelics.toMutableList()
        updated[slot] = EquippedRelic(id, 1)
        equippedRelics = updated.toList()
        relicRanks[replaced.id.ordinal] = 0
        relicRanks[id.ordinal] = 1
        message = RelicCatalog.byId(id).name.uppercase() + " // SLOT ${slot + 1} BOUND"
        messageTime = 1.7f
    }

    private fun meldRelic(slot: Int) {
        if (slot !in equippedRelics.indices) return
        val current = equippedRelics[slot]
        if (current.rank >= RelicCatalog.MAX_RANK) {
            grantMatter(8f)
            message = RelicCatalog.byId(current.id).name.uppercase() + " // RESONANCE SALVAGED"
        } else {
            val updated = equippedRelics.toMutableList()
            updated[slot] = current.copy(rank = current.rank + 1)
            equippedRelics = updated.toList()
            relicRanks[current.id.ordinal] = current.rank + 1
            message = RelicCatalog.byId(current.id).name.uppercase() + " // RANK " + (current.rank + 1)
        }
        messageTime = 1.7f
    }

    private fun clearRelicRuntime(id: RelicId) {
        val index = id.ordinal
        relicCooldowns[index] = 0f
        relicCounters[index] = 0
        when (id) {
            RelicId.SLIPSTREAM_RELAY -> slipstreamRelayTime = 0f
            RelicId.BRAKEPOINT_MEMORY -> brakepointCharge = 0f
            RelicId.BORROWED_MOMENT -> borrowedMomentTime = 0f
            else -> Unit
        }
        delayedRelicHits.removeAll { it.relicId == id }
        enemies.forEach { enemy ->
            enemy.relicCounters[index] = 0
            enemy.relicTimers[index] = 0f
            enemy.relicValues[index] = 0f
        }
    }

    private fun openNextPendingChoice() {
        if (phase != GamePhase.RUNNING) return
        when {
            pendingLevelChoices > 0 -> {
                pendingLevelChoices--
                openItemChoice()
            }
            pendingRelicChoices > 0 -> {
                pendingRelicChoices--
                openRelicChoice()
            }
        }
    }

    private fun amplifyCurrentWeapon() {
        weaponLevel++
        val reachedMastery = WeaponMastery.entries.firstOrNull { it.minimumLevel == weaponLevel }
        message = if (reachedMastery != null) {
            currentWeaponDefinition.name.uppercase() + " // " + reachedMastery.displayLabel.uppercase()
        } else {
            currentWeaponDefinition.name.uppercase() + " // LEVEL " + weaponLevel
        }
        messageTime = 1.5f
    }

    private fun acquireItem(itemId: Int) {
        val item = ItemCatalog.byId(itemId) ?: return
        if (itemStacks[itemId] >= item.maxStacks) {
            grantMatter((item.rarity.rank * 2).toFloat())
            message = item.name.uppercase() + " SALVAGED"
            messageTime = 1.4f
            return
        }
        itemStacks[itemId]++
        acquiredItemCount++
        recentItem = item
        applyItemModifier(item.primary)
        applyItemModifier(item.secondary)
        val family = item.id / 20
        familyStacks[family]++
        if (familyStacks[family] % 3 == 0) {
            weaponPower += 0.06f
            damageMultiplier += 0.04f
            message = item.family.uppercase() + " RESONANCE"
        } else {
            message = item.name.uppercase() + " ACQUIRED"
        }
        messageTime = 1.7f
        if (itemId !in discoveredItemIds) {
            if (externalAuthorities) {
                authorityCommands += GameCommand.ChangeProfile(ProfileChange.RecordItemDiscovery(itemId))
            } else {
                discoveredItemIds.add(itemId)
            }
        }
    }

    private fun applyItemModifier(modifier: ItemModifier) {
        when (modifier.effect) {
            ItemEffect.IMPACT_DAMAGE -> damageMultiplier += modifier.amount
            ItemEffect.WEAPON_POWER -> weaponPower += modifier.amount
            ItemEffect.MASS -> mass += modifier.amount
            ItemEffect.MAGNETISM -> magnetStrength *= 1f + modifier.amount
            ItemEffect.COOLING -> coolingRate *= 1f + modifier.amount
            ItemEffect.MAX_INTEGRITY -> {
                maxHp += modifier.amount
                hp += modifier.amount
            }
            ItemEffect.REGEN -> regenPerSecond += modifier.amount
            ItemEffect.DASH_POWER -> dashImpulse *= 1f + modifier.amount
            ItemEffect.DASH_EFFICIENCY -> dashHeatCost = max(12f, dashHeatCost * (1f - modifier.amount.coerceAtMost(0.25f)))
            ItemEffect.CRIT_CHANCE -> critChance = min(0.75f, critChance + modifier.amount)
            ItemEffect.CRIT_DAMAGE -> critMultiplier += modifier.amount
            ItemEffect.PICKUP_RADIUS -> pickupRadius += modifier.amount
            ItemEffect.LUCK -> luck += modifier.amount
            ItemEffect.DATA_GAIN -> dataGain += modifier.amount
            ItemEffect.MATTER_GAIN -> matterGain += modifier.amount
            ItemEffect.ATTACK_SPEED -> attackSpeed += modifier.amount
            ItemEffect.SHIELD_CAPACITY -> {
                maxShield += modifier.amount
                shield += modifier.amount
            }
            ItemEffect.DAMAGE_REDUCTION -> damageReduction = min(0.65f, damageReduction + modifier.amount)
            ItemEffect.COMBO_WINDOW -> comboWindow += modifier.amount
            ItemEffect.OVERDRIVE_GAIN -> overdriveGain += modifier.amount
        }
    }

    fun itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }
    fun isItemDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds
    fun metaLevel(id: MetaUpgradeId): Int = metaRanks[id.ordinal]
    fun isWeaponUnlocked(id: WeaponId): Boolean = id in unlockedWeaponSet

    fun buyMetaUpgrade(id: MetaUpgradeId): Boolean {
        if (externalAuthorities) {
            authorityCommands += GameCommand.ChangeProfile(ProfileChange.PurchaseMetaUpgrade(id))
            emitSound(SoundCue.PURCHASE)
            return true
        }
        val definition = MetaUpgradeCatalog.byId(id)
        val level = metaLevel(id)
        if (level >= definition.maxRanks) return false
        val cost = definition.cost(level).toLong()
        if (totalMatter < cost) return false
        totalMatter -= cost
        metaRanks[id.ordinal]++
        emitSound(SoundCue.PURCHASE)
        return true
    }

    fun buyOrEquipWeapon(id: WeaponId): Boolean {
        if (externalAuthorities) {
            authorityCommands += GameCommand.ChangeProfile(ProfileChange.PurchaseOrSelectWeapon(id))
            emitSound(SoundCue.PURCHASE)
            return true
        }
        if (id !in unlockedWeaponSet) {
            val cost = WeaponCatalog.byId(id).permanentUnlockCost.toLong()
            if (totalMatter < cost) return false
            totalMatter -= cost
            unlockedWeaponSet += id
            unlockedWeaponView = unlockedWeaponSet.toSet()
        }
        startingWeapon = id
        if (phase == GamePhase.MENU) weapon = id
        emitSound(SoundCue.PURCHASE)
        return true
    }

    private fun equipRunWeapon(id: WeaponId) {
        weapon = id
        resetWeaponRuntime()
        message = WeaponCatalog.byId(id).name.uppercase() + " SYNCHRONIZED"
        messageTime = 1.8f
    }

    private fun resetWeaponRuntime() {
        trail.clear()
        weaponNodes.clear()
        weaponOrbitals.clear()
        emitVisualFx(VisualFxCue.ClearWeaponArcs)
        projectiles.removeAll { !it.hostile }
        trailLastX = coreX
        trailLastY = coreY
        trailDistanceCarry = 0f
        morningstarAngle = 0f
        morningstarX = coreX + 105f
        morningstarY = coreY
        weaponClock = 0f
        weaponSecondaryClock = 0f
        weaponBeamTime = 0f
    }

    private fun grantMatter(base: Float) {
        matterFraction += base * matterGain
        val whole = floor(matterFraction).toLong()
        if (whole > 0L) {
            runMatter = saturatedAdd(runMatter, whole)
            matterFraction -= whole.toFloat()
        }
    }

    private fun bankRunMatter() {
        if (bankedThisRun) return
        if (externalAuthorities) {
            if (runMatter <= 0L && phase != GamePhase.VICTORY) return
            bankedThisRun = true
            authorityCommands += GameCommand.ChangeProfile(
                ProfileChange.ApplyRunOutcome(
                    matterEarned = runMatter.coerceAtLeast(0L),
                    clearedRebirthLevel = if (phase == GamePhase.VICTORY) rebirthLevel else null,
                ),
            )
            return
        }
        if (runMatter <= 0L) return
        bankedThisRun = true
        totalMatter = saturatedAdd(totalMatter, runMatter)
        lifetimeMatter = saturatedAdd(lifetimeMatter, runMatter)
    }

    internal fun takeSoundCues(): List<SoundCue> {
        if (soundCues.isEmpty()) return emptyList()
        val result = soundCues.toList()
        soundCues.clear()
        return result
    }

    internal fun takeAuthorityCommands(): List<GameCommand> {
        if (authorityCommands.isEmpty()) return emptyList()
        return authorityCommands.toList().also { authorityCommands.clear() }
    }

    /** Replaces only captured replicas; authority remains in Settings and Profile. */
    internal fun observeDependencies(
        observedSettings: GameSettings,
        profile: GameProfileReplica,
    ) {
        settings = observedSettings.normalized()
        totalMatter = profile.matter.coerceAtLeast(0L)
        lifetimeMatter = profile.lifetimeMatter.coerceAtLeast(totalMatter)
        coreShape = profile.coreShape
        startingWeapon = profile.selectedWeapon
        if (phase == GamePhase.MENU) weapon = profile.selectedWeapon
        unlockedWeaponSet.clear()
        unlockedWeaponSet.addAll(profile.unlockedWeapons)
        unlockedWeaponSet += WeaponId.FLUX_WAKE
        unlockedWeaponView = unlockedWeaponSet.toSet()
        profile.metaRanks.forEachIndexed { index, rank ->
            if (index in metaRanks.indices) {
                metaRanks[index] = rank.coerceIn(0, MetaUpgradeCatalog.all[index].maxRanks)
            }
        }
        discoveredItemIds.clear()
        discoveredItemIds.addAll(
            profile.discoveredItemIds.filter { it in 0 until ItemCatalog.ITEM_COUNT },
        )
        rebirthLevel = profile.rebirthLevel.coerceIn(0, RebirthProgression.MAX_LEVEL)
        highestClearedRebirth = profile.highestClearedRebirth.coerceIn(-1, rebirthLevel)
        activeRebirthProfile = profile.activeRebirthProfile
        upcomingRebirthProfile = profile.nextRebirthProfile
    }

    internal fun takeVisualFxCues(): List<VisualFxCue> = visualFxCues.drain()

    private fun emitSound(cue: SoundCue) {
        if (soundCues.size < 32) soundCues += cue
    }

    private fun emitVisualFx(cue: VisualFxCue) {
        visualFxCues.record(cue)
    }

    private fun endRun(reason: String) {
        if (phase != GamePhase.RUNNING) return
        phase = GamePhase.GAME_OVER
        message = reason
        messageTime = 10f
        keyboardBrakeActive = false
        secondaryBrakeActive = false
        touchBrakeActive = false
        updateBraking()
        dashBufferTime = 0f
        burst(coreX, coreY, 45, 4)
        bankRunMatter()
        emitSound(SoundCue.GAME_OVER)
    }

    private fun firePlayerProjectile(
        x: Float,
        y: Float,
        angle: Float,
        projectileSpeed: Float,
        damage: Float,
        pierce: Int,
        radius: Float,
        colorIndex: Int,
    ) {
        addProjectile(Projectile(
            x = x,
            y = y,
            vx = cos(angle) * projectileSpeed,
            vy = sin(angle) * projectileSpeed,
            radius = radius,
            life = 4f,
            hostile = false,
            damage = damage,
            pierce = pierce,
            colorIndex = colorIndex,
            sourceWeapon = weapon,
        ))
    }

    private fun fireSpread(x: Float, y: Float, angle: Float, count: Int, spacing: Float, projectileSpeed: Float) {
        val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
        val offset = (count - 1) * spacing * 0.5f
        repeat(count) { index ->
            val shotAngle = angle - offset + index * spacing
            addProjectile(Projectile(x, y, cos(shotAngle) * scaledSpeed, sin(shotAngle) * scaledSpeed, 5f, 6f))
        }
    }

    private fun fireProjectileWall(
        x: Float,
        y: Float,
        angle: Float,
        count: Int,
        spacing: Float,
        projectileSpeed: Float,
    ) {
        val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
        val directionX = cos(angle)
        val directionY = sin(angle)
        val tangentX = -directionY
        val tangentY = directionX
        val offset = (count - 1) * spacing * 0.5f
        repeat(count) { index ->
            val lateral = index * spacing - offset
            addProjectile(Projectile(
                x = x + tangentX * lateral,
                y = y + tangentY * lateral,
                vx = directionX * scaledSpeed,
                vy = directionY * scaledSpeed,
                radius = 5f,
                life = 6f,
            ))
        }
    }

    private fun fireRadial(x: Float, y: Float, count: Int, projectileSpeed: Float, rotation: Float) {
        val scaledSpeed = projectileSpeed * rebirthProfile.enemySpeedMultiplier
        repeat(count) { index ->
            val angle = rotation + index * TAU / count
            addProjectile(Projectile(x, y, cos(angle) * scaledSpeed, sin(angle) * scaledSpeed, 5f, 8f))
        }
    }

    private fun addProjectile(projectile: Projectile) {
        if (projectiles.size < MAX_PROJECTILES) projectiles += projectile
    }

    private fun addPickup(pickup: Pickup) {
        if (pickups.size < MAX_PICKUPS) pickups += pickup
    }

    private fun burst(x: Float, y: Float, requestedCount: Int, colorIndex: Int) {
        emitVisualFx(
            VisualFxCue.Burst(
                x = x,
                y = y,
                requestedCount = requestedCount,
                colorIndex = colorIndex,
                density = settings.particleDensity,
            ),
        )
    }

    private fun directionalBurst(
        x: Float,
        y: Float,
        requestedCount: Int,
        colorIndex: Int,
        directionX: Float,
        directionY: Float,
    ) {
        emitVisualFx(
            VisualFxCue.DirectionalBurst(
                x = x,
                y = y,
                requestedCount = requestedCount,
                colorIndex = colorIndex,
                directionX = directionX,
                directionY = directionY,
                density = settings.particleDensity,
            ),
        )
    }

    private fun shockwave(x: Float, y: Float, life: Float, maxRadius: Float, colorIndex: Int) {
        emitVisualFx(
            VisualFxCue.ShockwaveAdded(
                x = x,
                y = y,
                life = life,
                maxRadius = maxRadius,
                colorIndex = colorIndex,
            ),
        )
    }

    private fun addWeaponArc(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        life: Float = 0.14f,
    ) {
        emitVisualFx(
            VisualFxCue.WeaponArcAdded(
                fromX = fromX,
                fromY = fromY,
                toX = toX,
                toY = toY,
                life = life,
            ),
        )
    }

    private fun nearestEnemy(x: Float, y: Float, range: Float): Enemy? = enemies
        .asSequence()
        .filter { !it.dead && it.hp > 0f && distanceSquared(x, y, it.x, it.y) <= range * range }
        .minByOrNull { distanceSquared(x, y, it.x, it.y) }

    private fun movementAngle(): Float {
        if (speed > 20f) return atan2(velocityY, velocityX)
        val targetX = cameraX + pointerX - screenWidth * 0.5f
        val targetY = cameraY + pointerY - screenHeight * 0.5f
        return atan2(targetY - coreY, targetX - coreX)
    }

    private fun effectiveWeaponPower(): Float =
        weaponPower *
            rebirthProfile.playerPowerMultiplier *
            (1f + (weaponLevel - 1) * 0.08f) *
            (1f + currentWeaponMastery.damageBonus) *
            if (overdriveTime > 0f) 1.45f else 1f

    private fun cooldown(base: Float): Float =
        base / (
            attackSpeed *
                (1f + currentWeaponMastery.activationSpeedBonus) *
                (1f + relicActivationSpeedBonus()) *
                if (overdriveTime > 0f) 1.35f else 1f
            ).coerceAtLeast(0.2f)

    private fun relicActivationSpeedBonus(): Float {
        var bonus = 0f
        if (slipstreamRelayTime > 0f) bonus += 0.06f * relicRank(RelicId.SLIPSTREAM_RELAY)
        if (borrowedMomentTime > 0f) bonus += 0.09f * relicRank(RelicId.BORROWED_MOMENT)
        if (hp <= maxHp * 0.35f) bonus += 0.08f * relicRank(RelicId.LAST_LIGHT)
        val crownRank = relicRank(RelicId.CROWN_OF_FOUR_WINDS)
        if (crownRank > 0) bonus += 0.03f * crownRank * distinctRelicAspectCount()
        if (overdriveTime > 0f) bonus += 0.10f * relicRank(RelicId.ENGINE_OF_PARADOX)
        return bonus
    }

    private fun compactItemDescription(item: ItemDefinition): String =
        formatModifier(item.primary) + "  //  " + formatModifier(item.secondary)

    private fun formatModifier(modifier: ItemModifier): String {
        val amount = if (modifier.effect.unit == ModifierUnit.PERCENT) modifier.amount * 100f else modifier.amount
        val rounded = (amount * 10f).toInt() / 10f
        val suffix = when (modifier.effect.unit) {
            ModifierUnit.PERCENT -> "%"
            ModifierUnit.PER_SECOND -> "/s"
            ModifierUnit.SECONDS -> "s"
            ModifierUnit.FLAT -> ""
        }
        return "+" + rounded + suffix + " " + modifier.effect.displayLabel
    }

    private fun handleMenuPress(x: Float, y: Float) {
        val cardY = screenHeight * 0.62f
        if (y in cardY - d(55f)..cardY + d(55f)) {
            val center = screenWidth * 0.5f
            when {
                x in center - d(190f)..center - d(70f) -> setCoreShape(CoreShape.ORB)
                x in center - d(60f)..center + d(60f) -> setCoreShape(CoreShape.PRISM)
                x in center + d(70f)..center + d(190f) -> setCoreShape(CoreShape.SHARD)
            }
        }
        val buttonY = screenHeight * 0.78f
        if (x in screenWidth * 0.5f - d(150f)..screenWidth * 0.5f + d(150f) && y in buttonY - d(31f)..buttonY + d(31f)) {
            startRun()
            return
        }
        val secondaryY = screenHeight * 0.9f
        if (y !in secondaryY - d(20f)..secondaryY + d(20f)) return
        val spacing = min(d(132f), screenWidth * 0.19f)
        val start = screenWidth * 0.5f - spacing * 2f
        val index = ((x - start) / spacing).roundToInt()
        if (index !in 0..4) return
        val itemCenter = start + index * spacing
        if (x !in itemCenter - spacing * 0.44f..itemCenter + spacing * 0.44f) return
        when (index) {
            0 -> openLab()
            1 -> openArmory()
            2 -> openRebirth()
            3 -> openCodex()
            4 -> openSettings()
        }
    }

    private fun handlePausePress(x: Float, y: Float) {
        val center = screenWidth * 0.5f
        if (x !in center - d(150f)..center + d(150f)) return
        when {
            y in screenHeight * 0.5f..screenHeight * 0.5f + d(52f) -> togglePause()
            y in screenHeight * 0.62f..screenHeight * 0.62f + d(52f) -> openSettings()
            y in screenHeight * 0.74f..screenHeight * 0.74f + d(52f) -> returnToMenu()
        }
    }

    private fun handleEndPress(x: Float, y: Float) {
        val centerX = screenWidth * 0.5f
        val buttonY = screenHeight * 0.72f
        if (x in centerX - d(155f)..centerX + d(155f) && y in buttonY - d(38f)..buttonY + d(38f)) {
            startRun()
            return
        }
        if (
            phase == GamePhase.VICTORY &&
            x in centerX - d(120f)..centerX + d(120f) &&
            y in buttonY + d(50f)..buttonY + d(90f)
        ) {
            openRebirth()
            return
        }
        if (y > buttonY + d(if (phase == GamePhase.VICTORY) 100f else 50f)) returnToMenu()
    }

    private fun handleChoicePress(x: Float, y: Float) {
        val choiceCount = choices.size.coerceAtLeast(1)
        val gap = d(if (choiceCount >= 4) 10f else 18f)
        val maxCardWidth = d(
            when {
                choiceCount >= 4 -> 190f
                choiceCount == 3 -> 250f
                else -> 300f
            },
        )
        val availableCardWidth = (
            screenWidth - d(30f) - gap * (choiceCount - 1)
            ) / choiceCount
        val cardWidth = min(maxCardWidth, availableCardWidth).coerceAtLeast(d(92f))
        val total = cardWidth * choiceCount + gap * (choiceCount - 1)
        val startX = (screenWidth - total) * 0.5f
        val top = screenHeight * if (choiceCount >= 4) 0.29f else 0.31f
        val bottomReserve = d(if (choicesCanReroll) 105f else 35f)
        val cardHeight = min(d(270f), screenHeight - bottomReserve - top).coerceAtLeast(d(170f))
        if (y in top..top + cardHeight) {
            repeat(choiceCount) { index ->
                val left = startX + index * (cardWidth + gap)
                if (x in left..left + cardWidth) {
                    choose(index)
                    return
                }
            }
        }
        val rerollY = screenHeight - d(72f)
        if (x in screenWidth * 0.5f - d(90f)..screenWidth * 0.5f + d(90f) && y in rerollY - d(22f)..rerollY + d(22f)) {
            rerollChoices()
        }
    }

    private fun handleOverlayPress(x: Float, y: Float) {
        when (screen) {
            UiScreen.SETTINGS -> handleSettingsPress(x, y)
            UiScreen.LAB -> handleLabPress(x, y)
            UiScreen.ARMORY -> handleArmoryPress(x, y)
            UiScreen.REBIRTH -> handleRebirthPress(x, y)
            UiScreen.CODEX -> handleCodexPress(x, y)
            UiScreen.GAME -> Unit
        }
    }

    private fun overlayBounds(maxWidth: Float = 900f, maxHeight: Float = 650f): FloatArray {
        val width = min(d(maxWidth), screenWidth - d(30f))
        val height = min(d(maxHeight), screenHeight - d(30f))
        val left = (screenWidth - width) * 0.5f
        val top = (screenHeight - height) * 0.5f
        return floatArrayOf(left, top, left + width, top + height)
    }

    private fun handleSettingsPress(x: Float, y: Float) {
        val bounds = overlayBounds(640f, 620f)
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        val startY = top + d(72f)
        val settingsBottom = bottom - d(64f)
        val availableHeight = settingsBottom - startY
        val rowsPerPage = settingsRowsPerPage(availableHeight, uiScale)
        val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
        settingsPage = settingsPage.coerceIn(0, maxPage)
        if (y > bottom - d(55f)) {
            if (x !in left..right) return
            if (maxPage == 0) {
                if (x in left + d(20f)..right - d(20f)) closeOverlay()
            } else {
                when {
                    x < left + (right - left) * 0.45f -> closeOverlay()
                    x < right - d(85f) -> {
                        settingsPage = max(0, settingsPage - 1)
                        emitSound(SoundCue.UI_CLICK)
                    }
                    else -> {
                        settingsPage = min(maxPage, settingsPage + 1)
                        emitSound(SoundCue.UI_CLICK)
                    }
                }
            }
            return
        }
        val pageStart = settingsPage * rowsPerPage
        val visibleCount = min(rowsPerPage, SettingsRow.entries.size - pageStart)
        val spacing = min(d(48f), availableHeight / visibleCount)
        if (spacing <= 0f) return
        val visibleRowIndex = floor((y - startY) / spacing).toInt()
        val rowIndex = pageStart + visibleRowIndex
        if (visibleRowIndex !in 0 until visibleCount || rowIndex !in SettingsRow.entries.indices) return
        if (x !in right - d(190f)..right - d(20f)) return
        val rowTop = startY + spacing * visibleRowIndex
        if (y > rowTop + spacing - d(4f)) return
        val direction = if (x < right - d(105f)) -1 else 1
        adjustSetting(SettingsRow.entries[rowIndex], direction)
    }

    private fun adjustSetting(row: SettingsRow, direction: Int) {
        if (externalAuthorities) {
            val change = when (row) {
                SettingsRow.SFX -> SettingsChange.ToggleSound
                SettingsRow.MUSIC -> SettingsChange.ToggleMusic
                SettingsRow.MASTER_VOLUME -> SettingsChange.AdjustMasterVolume(direction)
                SettingsRow.SIMULATION_SPEED -> SettingsChange.AdjustSimulationSpeed(direction)
                SettingsRow.TEXT_SIZE -> SettingsChange.AdjustTextScale(direction)
                SettingsRow.SCREEN_SHAKE -> SettingsChange.ToggleScreenShake
                SettingsRow.PARTICLES -> SettingsChange.AdjustParticleDensity(direction)
                SettingsRow.DAMAGE_NUMBERS -> SettingsChange.ToggleDamageNumbers
                SettingsRow.DAMAGE_NUMBER_SIZE -> SettingsChange.AdjustDamageNumberSize(direction)
                SettingsRow.DAMAGE_NUMBER_FORMAT -> SettingsChange.AdjustDamageNumberFormat(direction)
                SettingsRow.DAMAGE_COLOR_THRESHOLDS ->
                    SettingsChange.AdjustDamageNumberTierThreshold(direction)
            }
            authorityCommands += GameCommand.ChangeSettings(change)
            emitSound(SoundCue.UI_CLICK)
            return
        }
        settings = when (row) {
            SettingsRow.SFX -> settings.copy(soundEnabled = !settings.soundEnabled)
            SettingsRow.MUSIC -> settings.copy(musicEnabled = !settings.musicEnabled)
            SettingsRow.MASTER_VOLUME -> settings.copy(
                masterVolume = stepPercentage(settings.masterVolume, direction, 0f, 1f),
            )
            SettingsRow.SIMULATION_SPEED -> {
                val current = SIMULATION_SPEEDS.indices.minByOrNull { kotlin.math.abs(SIMULATION_SPEEDS[it] - settings.simulationSpeed) } ?: 2
                settings.copy(simulationSpeed = SIMULATION_SPEEDS[(current + direction).coerceIn(SIMULATION_SPEEDS.indices)])
            }
            SettingsRow.TEXT_SIZE -> settings.copy(
                textScale = stepPercentage(settings.textScale, direction, 1f, 1.75f),
            )
            SettingsRow.SCREEN_SHAKE -> settings.copy(screenShake = !settings.screenShake)
            SettingsRow.PARTICLES -> {
                val next = (settings.particleDensity.ordinal + direction).coerceIn(ParticleDensity.entries.indices)
                settings.copy(particleDensity = ParticleDensity.entries[next])
            }
            SettingsRow.DAMAGE_NUMBERS -> settings.copy(damageNumbers = !settings.damageNumbers)
            SettingsRow.DAMAGE_NUMBER_SIZE -> {
                val next = (settings.damageNumberSize.ordinal + direction).coerceIn(DamageNumberSize.entries.indices)
                settings.copy(damageNumberSize = DamageNumberSize.entries[next])
            }
            SettingsRow.DAMAGE_NUMBER_FORMAT -> {
                val next = (settings.damageNumberFormat.ordinal + direction).coerceIn(DamageNumberFormat.entries.indices)
                settings.copy(damageNumberFormat = DamageNumberFormat.entries[next])
            }
            SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
                val current = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
                    kotlin.math.abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - settings.damageNumberTierThreshold)
                } ?: 2
                val next = (current + direction).coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
                settings.copy(damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[next])
            }
        }.normalized()
        emitSound(SoundCue.UI_CLICK)
    }

    private fun stepPercentage(value: Float, direction: Int, minimum: Float, maximum: Float): Float {
        val nextPercent = (value * 100f).roundToInt() + direction.coerceIn(-1, 1)
        return (nextPercent / 100f).coerceIn(minimum, maximum)
    }

    private fun handleLabPress(x: Float, y: Float) {
        val bounds = overlayBounds()
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        if (y > bottom - d(55f)) {
            closeOverlay()
            return
        }
        val contentTop = top + d(88f)
        val contentWidth = right - left - d(50f)
        val columnWidth = contentWidth * 0.5f
        val rowHeight = d(105f)
        if (x !in left + d(25f)..right - d(25f) || y !in contentTop..contentTop + rowHeight * 4f) return
        val column = if (x < left + d(25f) + columnWidth) 0 else 1
        val row = floor((y - contentTop) / rowHeight).toInt()
        if (row !in 0..3) return
        val index = row * 2 + column
        MetaUpgradeId.entries.getOrNull(index)?.let(::buyMetaUpgrade)
    }

    private fun handleArmoryPress(x: Float, y: Float) {
        val bounds = overlayBounds()
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        if (y > bottom - d(55f)) {
            when {
                x < left + (right - left) * 0.45f -> closeOverlay()
                x < right - d(85f) -> armoryPage = max(0, armoryPage - 1)
                else -> armoryPage = min(maxArmoryPage, armoryPage + 1)
            }
            emitSound(SoundCue.UI_CLICK)
            return
        }
        val cardWidth = min(d(245f), (right - left - d(80f)) / 3f)
        val gap = d(16f)
        val total = cardWidth * 3f + gap * 2f
        val startX = (screenWidth - total) * 0.5f
        val cardTop = top + d(118f)
        val cardBottom = bottom - d(85f)
        if (y !in cardTop..cardBottom) return
        repeat(3) { index ->
            val cardLeft = startX + index * (cardWidth + gap)
            if (x in cardLeft..cardLeft + cardWidth) {
                armoryPageWeapons.getOrNull(index)?.let { buyOrEquipWeapon(it.id) }
            }
        }
    }

    private fun handleRebirthPress(x: Float, y: Float) {
        val bounds = overlayBounds()
        val left = bounds[0]
        val right = bounds[2]
        val bottom = bounds[3]
        if (x in left + d(24f)..right - d(24f) && y in bottom - d(118f)..bottom - d(68f)) {
            requestRebirth()
            return
        }
        if (x in left + d(20f)..right - d(20f) && y in bottom - d(55f)..bottom - d(14f)) {
            closeOverlay()
        }
    }

    private fun handleCodexPress(x: Float, y: Float) {
        val bounds = overlayBounds()
        val left = bounds[0]
        val right = bounds[2]
        val bottom = bounds[3]
        if (y <= bottom - d(55f)) return
        when {
            x < left + (right - left) * 0.45f -> closeOverlay()
            x < right - d(85f) -> codexPage = max(0, codexPage - 1)
            else -> codexPage = min(maxCodexPage, codexPage + 1)
        }
        emitSound(SoundCue.UI_CLICK)
    }

    private fun inDashButton(x: Float, y: Float): Boolean {
        val cx = screenWidth - d(82f)
        val cy = screenHeight - d(88f)
        return distanceSquared(x, y, cx, cy) < square(d(48f))
    }

    private fun inBrakeButton(x: Float, y: Float): Boolean {
        val cx = screenWidth - d(190f)
        val cy = screenHeight - d(67f)
        return distanceSquared(x, y, cx, cy) < square(d(38f))
    }

    private fun rebaseWorldIfNeeded() {
        if (kotlin.math.abs(coreX) < 250_000f && kotlin.math.abs(coreY) < 250_000f) return
        val shiftX = coreX
        val shiftY = coreY
        coreX -= shiftX
        coreY -= shiftY
        previousCoreX -= shiftX
        previousCoreY -= shiftY
        previousSingularityX -= shiftX
        previousSingularityY -= shiftY
        cameraX -= shiftX
        cameraY -= shiftY
        trailLastX -= shiftX
        trailLastY -= shiftY
        enemies.forEach { it.x -= shiftX; it.y -= shiftY; it.previousX -= shiftX; it.previousY -= shiftY }
        projectiles.forEach { it.x -= shiftX; it.y -= shiftY; it.previousX -= shiftX; it.previousY -= shiftY }
        pickups.forEach { it.x -= shiftX; it.y -= shiftY }
        trail.forEach { it.x -= shiftX; it.y -= shiftY }
        emitVisualFx(VisualFxCue.WorldRebased(shiftX, shiftY))
        weaponNodes.forEach { it.x -= shiftX; it.y -= shiftY }
        weaponOrbitals.forEach { it.x -= shiftX; it.y -= shiftY }
        totem?.let { it.x -= shiftX; it.y -= shiftY }
        morningstarX -= shiftX
        morningstarY -= shiftY
        weaponBeamStartX -= shiftX
        weaponBeamStartY -= shiftY
        weaponBeamEndX -= shiftX
        weaponBeamEndY -= shiftY
    }

    internal fun toProjection(): GameProjection = GameProjection(
        phase = phase,
        screen = screen,
        settings = settings,
        rebirthLevel = rebirthLevel,
        highestClearedRebirth = highestClearedRebirth,
        rebirthProfile = activeRebirthProfile,
        nextRebirthProfile = upcomingRebirthProfile,
        rebirthConfirmationArmed = rebirthConfirmationArmed,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        uiScale = uiScale,
        coreX = coreX,
        coreY = coreY,
        velocityX = velocityX,
        velocityY = velocityY,
        cameraX = cameraX,
        cameraY = cameraY,
        pointerX = pointerX,
        pointerY = pointerY,
        pointerActive = pointerActive,
        braking = braking,
        elapsed = elapsed,
        heat = heat,
        overheated = overheated,
        dashPhaseTime = dashPhaseTime,
        hp = hp,
        maxHp = maxHp,
        shield = shield,
        maxShield = maxShield,
        level = level,
        data = data,
        nextLevelData = nextLevelData,
        keys = keys,
        kills = kills,
        combo = combo,
        comboTime = comboTime,
        runMatter = runMatter,
        totalMatter = totalMatter,
        lifetimeMatter = lifetimeMatter,
        lastImpact = lastImpact,
        lastImpactTime = lastImpactTime,
        damageFlash = damageFlash,
        runGrace = runGrace,
        screenShake = screenShake,
        message = message,
        messageTime = messageTime,
        mass = mass,
        damageMultiplier = damageMultiplier,
        weaponPower = weaponPower,
        coolingRate = coolingRate,
        magnetStrength = magnetStrength,
        dashImpulse = dashImpulse,
        dashHeatCost = dashHeatCost,
        regenPerSecond = regenPerSecond,
        critChance = critChance,
        critMultiplier = critMultiplier,
        pickupRadius = pickupRadius,
        luck = luck,
        dataGain = dataGain,
        matterGain = matterGain,
        attackSpeed = attackSpeed,
        damageReduction = damageReduction,
        comboWindow = comboWindow,
        overdriveGain = overdriveGain,
        dragCoefficient = dragCoefficient,
        polarityStability = polarityStability,
        weapon = weapon,
        startingWeapon = startingWeapon,
        weaponLevel = weaponLevel,
        overdriveCharge = overdriveCharge,
        overdriveTime = overdriveTime,
        rerollsRemaining = rerollsRemaining,
        acquiredItemCount = acquiredItemCount,
        recentItem = recentItem,
        equippedRelics = equippedRelics.toImmutableList(),
        morningstarAngle = morningstarAngle,
        morningstarX = morningstarX,
        morningstarY = morningstarY,
        weaponBeamTime = weaponBeamTime,
        weaponBeamStartX = weaponBeamStartX,
        weaponBeamStartY = weaponBeamStartY,
        weaponBeamEndX = weaponBeamEndX,
        weaponBeamEndY = weaponBeamEndY,
        totem = totem?.let { value -> TotemProjection(value.x, value.y, value.pulse) },
        codexPage = codexPage,
        armoryPage = armoryPage,
        settingsPage = settingsPage,
        coreShape = coreShape,
        enemies = enemies.map { value ->
            EnemyProjection(
                id = value.id,
                type = value.type,
                x = value.x,
                y = value.y,
                vx = value.vx,
                vy = value.vy,
                hp = value.hp,
                maxHp = value.maxHp,
                radius = value.radius,
                actionTimer = value.actionTimer,
                flash = value.flash,
                contactCooldown = value.contactCooldown,
                weaponCooldown = value.weaponCooldown,
                previousX = value.previousX,
                previousY = value.previousY,
                dead = value.dead,
            )
        }.toImmutableList(),
        projectiles = projectiles.map { value ->
            ProjectileProjection(
                x = value.x,
                y = value.y,
                vx = value.vx,
                vy = value.vy,
                radius = value.radius,
                life = value.life,
                hostile = value.hostile,
                damage = value.damage,
                pierce = value.pierce,
                colorIndex = value.colorIndex,
                sourceWeapon = value.sourceWeapon,
                previousX = value.previousX,
                previousY = value.previousY,
            )
        }.toImmutableList(),
        pickups = pickups.map { value ->
            PickupProjection(
                value.type,
                value.x,
                value.y,
                value.vx,
                value.vy,
                value.life,
                value.previousX,
                value.previousY,
            )
        }.toImmutableList(),
        trail = trail.map { value -> TrailPointProjection(value.x, value.y, value.age) }.toImmutableList(),
        weaponNodes = weaponNodes.map { value ->
            WeaponNodeProjection(value.type, value.x, value.y, value.life, value.maxLife, value.radius)
        }.toImmutableList(),
        weaponOrbitals = weaponOrbitals.map { value ->
            WeaponOrbitalProjection(value.index, value.x, value.y, value.radius)
        }.toImmutableList(),
        choices = choices.toImmutableList(),
        choiceType = activeChoiceType,
        pendingRelicChoiceCount = pendingRelicChoices,
        unlockedWeapons = unlockedWeaponView.toImmutableSet(),
        itemStacks = itemStacks.asIterable().toImmutableList(),
        discoveredItemIds = discoveredItemIds.toImmutableSet(),
        metaRanks = metaRanks.asIterable().toImmutableList(),
        relicRanks = relicRanks.asIterable().toImmutableList(),
    )

    internal fun boundedCollectionSizes(): List<Int> = buildList {
        add(enemies.size)
        add(projectiles.size)
        add(pickups.size)
        add(trail.size)
        add(weaponNodes.size)
        add(weaponOrbitals.size)
        add(choices.size)
        add(equippedRelics.size)
        add(unlockedWeaponSet.size)
        add(discoveredItemIds.size)
        add(delayedRelicHits.size)
        add(itemStacks.size)
        add(familyStacks.size)
        add(metaRanks.size)
        add(relicRanks.size)
        add(relicCooldowns.size)
        add(relicCounters.size)
        add(relicProcCounts.size)
        add(agonyMutationCounts.size)
        projectiles.forEach { projectile -> add(projectile.hitEnemyIds.size) }
    }

    internal fun domainCollectionLimits(): List<DomainCollectionLimit> = listOf(
        DomainCollectionLimit("enemies", enemies.size, MAX_ENEMIES),
        DomainCollectionLimit("projectiles", projectiles.size, MAX_PROJECTILES),
        DomainCollectionLimit("pickups", pickups.size, MAX_PICKUPS),
        DomainCollectionLimit("trail", trail.size, MAX_TRAIL_POINTS),
        DomainCollectionLimit("delayedRelicHits", delayedRelicHits.size, MAX_DELAYED_RELIC_HITS),
    )

    internal fun estimatedStateBytes(): Long =
        8_192L +
            enemies.size * 512L +
            projectiles.size * 256L +
            pickups.size * 96L +
            trail.size * 32L +
            weaponNodes.size * 56L +
            weaponOrbitals.size * 40L +
            choices.size * 512L +
            discoveredItemIds.size * 16L +
            delayedRelicHits.size * 48L +
            projectiles.sumOf { projectile -> projectile.hitEnemyIds.size.toLong() * 16L }

    /**
     * Produces an isolated transaction candidate. Accepted instances are never
     * mutated again; all mutation happens on this private copy inside decide.
     */
    internal fun copyForDecision(): MutableGameState {
        val target = MutableGameState(
            seed = 0,
            initialMatter = 0,
            initialRebirthLevel = rebirthLevel,
            externalAuthorities = externalAuthorities,
        )

        target.gameplayRandom = gameplayRandom.copy()
        target.activeRebirthProfile = activeRebirthProfile
        target.upcomingRebirthProfile = upcomingRebirthProfile
        target.unlockedWeaponSet.clear()
        target.unlockedWeaponSet.addAll(unlockedWeaponSet)
        target.unlockedWeaponView = target.unlockedWeaponSet.toSet()
        metaRanks.copyInto(target.metaRanks)
        target.discoveredItemIds.clear()
        target.discoveredItemIds.addAll(discoveredItemIds)
        itemStacks.copyInto(target.itemStacks)
        familyStacks.copyInto(target.familyStacks)
        target.soundCues.clear()
        target.soundCues.addAll(soundCues)
        target.authorityCommands.clear()
        target.authorityCommands.addAll(authorityCommands)
        target.visualFxCues = visualFxCues.copy()

        target.nextEntityId = nextEntityId
        target.spawnClock = spawnClock
        target.nextEliteAt = nextEliteAt
        target.dashBufferTime = dashBufferTime
        target.bossSpawned = bossSpawned
        target.lastPointerPressed = lastPointerPressed
        target.keyboardBrakeActive = keyboardBrakeActive
        target.secondaryBrakeActive = secondaryBrakeActive
        target.touchBrakeActive = touchBrakeActive
        target.uiScale = uiScale
        target.accumulator = accumulator
        target.lastTransitionSteps = lastTransitionSteps
        target.previousCoreX = previousCoreX
        target.previousCoreY = previousCoreY
        target.previousSingularityX = previousSingularityX
        target.previousSingularityY = previousSingularityY
        target.trailLastX = trailLastX
        target.trailLastY = trailLastY
        target.trailDistanceCarry = trailDistanceCarry
        target.weaponClock = weaponClock
        target.weaponSecondaryClock = weaponSecondaryClock
        target.pendingLevelChoices = pendingLevelChoices
        target.pendingRelicChoices = pendingRelicChoices
        target.pendingBindingRelic = pendingBindingRelic
        target.pendingRelicBindAction = pendingRelicBindAction
        relicRanks.copyInto(target.relicRanks)
        relicCooldowns.copyInto(target.relicCooldowns)
        relicCounters.copyInto(target.relicCounters)
        relicProcCounts.copyInto(target.relicProcCounts)
        target.delayedRelicHits.clear()
        target.delayedRelicHits.addAll(delayedRelicHits.map(DelayedRelicHit::copy))
        agonyMutationCounts.copyInto(target.agonyMutationCounts)
        target.slipstreamRelayTime = slipstreamRelayTime
        target.borrowedMomentTime = borrowedMomentTime
        target.brakepointCharge = brakepointCharge
        target.dataFraction = dataFraction
        target.matterFraction = matterFraction
        target.shieldRechargeDelay = shieldRechargeDelay
        target.overheatHoldTime = overheatHoldTime
        target.saturationHeadingX = saturationHeadingX
        target.saturationHeadingY = saturationHeadingY
        target.timeSinceDamage = timeSinceDamage
        target.hurtCooldown = hurtCooldown
        target.lastAimDirectionX = lastAimDirectionX
        target.lastAimDirectionY = lastAimDirectionY
        target.bankedThisRun = bankedThisRun
        target.overlayReturnPhase = overlayReturnPhase
        target.activeChoiceType = activeChoiceType

        target.phase = phase
        target.screen = screen
        target.settings = settings
        target.rebirthLevel = rebirthLevel
        target.highestClearedRebirth = highestClearedRebirth
        target.rebirthConfirmationArmed = rebirthConfirmationArmed
        target.screenWidth = screenWidth
        target.screenHeight = screenHeight
        target.coreX = coreX
        target.coreY = coreY
        target.velocityX = velocityX
        target.velocityY = velocityY
        target.cameraX = cameraX
        target.cameraY = cameraY
        target.pointerX = pointerX
        target.pointerY = pointerY
        target.pointerActive = pointerActive
        target.braking = braking
        target.elapsed = elapsed
        target.heat = heat
        target.overheated = overheated
        target.dashPhaseTime = dashPhaseTime
        target.hp = hp
        target.maxHp = maxHp
        target.shield = shield
        target.maxShield = maxShield
        target.level = level
        target.data = data
        target.nextLevelData = nextLevelData
        target.keys = keys
        target.kills = kills
        target.combo = combo
        target.comboTime = comboTime
        target.runMatter = runMatter
        target.totalMatter = totalMatter
        target.lifetimeMatter = lifetimeMatter
        target.lastImpact = lastImpact
        target.lastImpactTime = lastImpactTime
        target.damageFlash = damageFlash
        target.runGrace = runGrace
        target.screenShake = screenShake
        target.message = message
        target.messageTime = messageTime
        target.mass = mass
        target.damageMultiplier = damageMultiplier
        target.weaponPower = weaponPower
        target.coolingRate = coolingRate
        target.magnetStrength = magnetStrength
        target.dashImpulse = dashImpulse
        target.dashHeatCost = dashHeatCost
        target.regenPerSecond = regenPerSecond
        target.critChance = critChance
        target.critMultiplier = critMultiplier
        target.pickupRadius = pickupRadius
        target.luck = luck
        target.dataGain = dataGain
        target.matterGain = matterGain
        target.attackSpeed = attackSpeed
        target.damageReduction = damageReduction
        target.comboWindow = comboWindow
        target.overdriveGain = overdriveGain
        target.dragCoefficient = dragCoefficient
        target.polarityStability = polarityStability
        target.weapon = weapon
        target.startingWeapon = startingWeapon
        target.weaponLevel = weaponLevel
        target.overdriveCharge = overdriveCharge
        target.overdriveTime = overdriveTime
        target.rerollsRemaining = rerollsRemaining
        target.acquiredItemCount = acquiredItemCount
        target.recentItem = recentItem
        target.equippedRelics = equippedRelics.toList()
        target.morningstarAngle = morningstarAngle
        target.morningstarX = morningstarX
        target.morningstarY = morningstarY
        target.weaponBeamTime = weaponBeamTime
        target.weaponBeamStartX = weaponBeamStartX
        target.weaponBeamStartY = weaponBeamStartY
        target.weaponBeamEndX = weaponBeamEndX
        target.weaponBeamEndY = weaponBeamEndY
        target.totem = totem?.copy()
        target.codexPage = codexPage
        target.armoryPage = armoryPage
        target.settingsPage = settingsPage
        target.coreShape = coreShape

        target.enemies.clear()
        target.enemies.addAll(enemies.map { enemy ->
            enemy.copy(
                relicCounters = enemy.relicCounters.copyOf(),
                relicTimers = enemy.relicTimers.copyOf(),
                relicValues = enemy.relicValues.copyOf(),
            )
        })
        target.projectiles.clear()
        target.projectiles.addAll(projectiles.map { projectile ->
            projectile.copy(hitEnemyIds = projectile.hitEnemyIds.toMutableSet())
        })
        target.pickups.clear()
        target.pickups.addAll(pickups.map(Pickup::copy))
        target.trail.clear()
        target.trail.addAll(trail.map(TrailPoint::copy))
        target.weaponNodes.clear()
        target.weaponNodes.addAll(weaponNodes.map(WeaponNode::copy))
        target.weaponOrbitals.clear()
        target.weaponOrbitals.addAll(weaponOrbitals.map(WeaponOrbital::copy))
        target.choices = choices.toList()
        return target
    }

    internal fun setVelocityForTesting(x: Float, y: Float) {
        velocityX = x
        velocityY = y
    }

    internal fun addEnemyForTesting(
        x: Float,
        y: Float,
        hp: Float = 1_000f,
        radius: Float = 17f,
        type: EnemyType = EnemyType.DRIFTER,
    ): Enemy {
        val enemy = Enemy(nextEntityId++, type, x, y, hp = hp, maxHp = hp, radius = radius)
        enemies += enemy
        return enemy
    }

    internal fun activateTotemForTesting(x: Float = coreX, y: Float = coreY) {
        keys++
        totem = Totem(x, y)
    }

    internal fun equipWeaponForTesting(id: WeaponId) = equipRunWeapon(id)

    internal fun acquireItemForTesting(id: Int) = acquireItem(id)

    internal fun acquireRelicForTesting(id: RelicId) = acquireRelic(id)

    internal fun openRelicChoiceForTesting() {
        pendingRelicChoices++
        openNextPendingChoice()
    }

    internal fun dropRelicForTesting(x: Float = coreX, y: Float = coreY) {
        pickups += Pickup(PickupType.RELIC, x, y)
    }

    internal fun addProjectileForTesting() {
        projectiles += Projectile(coreX, coreY, 0f, 0f, 1f, 1f)
    }

    internal fun addTrailPointForTesting() {
        trail += TrailPoint(coreX, coreY, 0f)
    }

    internal fun addDelayedRelicHitForTesting() {
        delayedRelicHits += DelayedRelicHit(RelicId.ECHO_CHAMBER, 0, 1f, 1f)
    }

    internal fun killEnemyForTesting(
        type: EnemyType,
        x: Float = coreX,
        y: Float = coreY,
    ) {
        val enemy = addEnemyForTesting(x, y, hp = 1f, type = type)
        damageEnemy(enemy, 2f, relicKillProcsEligible = true)
        onEnemyKilled(enemy)
        enemies.remove(enemy)
    }

    internal fun triggerWeaponContactForTesting(target: Enemy, continuous: Boolean = false): Float =
        dealWeaponDamage(
            target,
            baseAmount = 20f,
            cadence = if (continuous) WeaponHitCadence.CONTINUOUS else WeaponHitCadence.DISCRETE,
        ).amount

    internal fun damageEnemyForTesting(target: Enemy, amount: Float): Float =
        damageEnemy(target, amount).amount

    internal fun relicProcCountForTesting(id: RelicId): Int = relicProcCounts[id.ordinal]

    internal fun agonyMutationCountForTesting(id: WeaponId): Int = agonyMutationCounts[id.ordinal]

    internal fun agonyMutationCountsForTesting(): List<Int> = agonyMutationCounts.toList()

    internal fun delayedRelicHitCountForTesting(): Int = delayedRelicHits.size

    internal fun grantDataForTesting(amount: Float) = gainData(amount)

    internal fun amplifyWeaponForTesting(levels: Int = 1) {
        repeat(max(0, levels)) { amplifyCurrentWeapon() }
    }

    internal fun markCurrentRebirthClearedForTesting() {
        highestClearedRebirth = max(highestClearedRebirth, rebirthLevel)
        phase = GamePhase.VICTORY
    }

    private fun d(value: Float): Float = value * uiScale
    private fun square(value: Float): Float = value * value
    private fun powFast(base: Float, exponent: Int): Float {
        var result = 1f
        repeat(exponent) { result *= base }
        return result
    }
}

private fun saturatedAdd(left: Long, right: Long): Long {
    if (right <= 0L) return left
    return if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

fun formatRunTime(seconds: Float): String {
    val total = max(0, seconds.toInt())
    val minutes = total / 60
    val remaining = total % 60
    return minutes.toString().padStart(2, '0') + ":" + remaining.toString().padStart(2, '0')
}

internal fun enemyTypeForElapsed(elapsed: Float, roll: Float): EnemyType {
    val normalizedRoll = clamp(roll, 0f, 0.999_999f)
    return when {
        elapsed < 38f -> EnemyType.DRIFTER
        elapsed < 90f -> when {
            normalizedRoll < 0.70f -> EnemyType.DRIFTER
            else -> EnemyType.SHOOTER
        }
        elapsed < 150f -> when {
            normalizedRoll < 0.50f -> EnemyType.DRIFTER
            normalizedRoll < 0.78f -> EnemyType.SHOOTER
            else -> EnemyType.INTERCEPTOR
        }
        elapsed < 240f -> when {
            normalizedRoll < 0.34f -> EnemyType.DRIFTER
            normalizedRoll < 0.58f -> EnemyType.SHOOTER
            normalizedRoll < 0.76f -> EnemyType.CHARGER
            normalizedRoll < 0.92f -> EnemyType.INTERCEPTOR
            else -> EnemyType.WEAVER
        }
        elapsed < 360f -> when {
            normalizedRoll < 0.26f -> EnemyType.DRIFTER
            normalizedRoll < 0.46f -> EnemyType.SHOOTER
            normalizedRoll < 0.62f -> EnemyType.CHARGER
            normalizedRoll < 0.77f -> EnemyType.INTERCEPTOR
            normalizedRoll < 0.87f -> EnemyType.WEAVER
            normalizedRoll < 0.95f -> EnemyType.SPLITTER
            else -> EnemyType.WARDEN
        }
        else -> when {
            normalizedRoll < 0.22f -> EnemyType.DRIFTER
            normalizedRoll < 0.39f -> EnemyType.SHOOTER
            normalizedRoll < 0.53f -> EnemyType.CHARGER
            normalizedRoll < 0.67f -> EnemyType.INTERCEPTOR
            normalizedRoll < 0.79f -> EnemyType.WEAVER
            normalizedRoll < 0.91f -> EnemyType.SPLITTER
            else -> EnemyType.WARDEN
        }
    }
}
