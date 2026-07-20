// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.game.domain.projection

import kinetickk.core.collections.ImmutableList
import kinetickk.core.collections.ImmutableSet
import kinetickk.core.collections.toImmutableList
import kinetickk.feature.game.domain.model.ChoiceOption
import kinetickk.feature.game.domain.model.ChoiceType
import kinetickk.feature.game.domain.model.clamp
import kinetickk.feature.game.domain.model.CoreShape
import kinetickk.feature.game.domain.model.DamageNumberFormat
import kinetickk.feature.game.domain.model.EnemyType
import kinetickk.feature.game.domain.model.EquippedRelic
import kinetickk.feature.game.domain.model.formatDamageNumber
import kinetickk.feature.game.domain.model.GamePhase
import kinetickk.feature.game.domain.model.GameSettings
import kinetickk.feature.game.domain.model.ItemCatalog
import kinetickk.feature.game.domain.model.ItemDefinition
import kinetickk.feature.game.domain.model.length
import kinetickk.feature.game.domain.model.MetaUpgradeId
import kinetickk.feature.game.domain.model.PickupType
import kinetickk.feature.game.domain.model.RebirthProfile
import kinetickk.feature.game.domain.model.RebirthProgression
import kinetickk.feature.game.domain.model.RelicId
import kinetickk.feature.game.domain.model.UiScreen
import kinetickk.feature.game.domain.model.WeaponCatalog
import kinetickk.feature.game.domain.model.WeaponDefinition
import kinetickk.feature.game.domain.model.WeaponId
import kinetickk.feature.game.domain.model.WeaponMastery
import kinetickk.feature.game.domain.model.WeaponNodeType
import kotlin.math.min

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
)

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

data class TrailPointProjection(val x: Float, val y: Float, val age: Float)

data class ParticleProjection(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val colorIndex: Int,
    val size: Float,
)

data class MotionEchoProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val intensity: Float,
)

data class ShockwaveProjection(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val maxRadius: Float,
    val colorIndex: Int,
)

data class DamageNumberProjection(
    val x: Float,
    val y: Float,
    val amount: Long,
    val critical: Boolean,
    val life: Float,
    val compactAmount: String = formatDamageNumber(amount, DamageNumberFormat.COMPACT),
    val fullAmount: String = formatDamageNumber(amount, DamageNumberFormat.FULL),
) {
    fun formattedAmount(format: DamageNumberFormat): String = when (format) {
        DamageNumberFormat.COMPACT -> compactAmount
        DamageNumberFormat.FULL -> fullAmount
    }
}

data class TotemProjection(val x: Float, val y: Float, val pulse: Float)

data class WeaponNodeProjection(
    val type: WeaponNodeType,
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val radius: Float,
)

data class WeaponOrbitalProjection(val index: Int, val x: Float, val y: Float, val radius: Float)

data class WeaponArcProjection(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val life: Float,
)

/**
 * Immutable render payload. No mutable simulation container crosses this boundary.
 */
class GameProjection internal constructor(
    val phase: GamePhase,
    val screen: UiScreen,
    val settings: GameSettings,
    val rebirthLevel: Int,
    val highestClearedRebirth: Int,
    val rebirthConfirmationArmed: Boolean,
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
    val combo: Int,
    val comboTime: Float,
    val runMatter: Long,
    val totalMatter: Long,
    val lifetimeMatter: Long,
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
    val startingWeapon: WeaponId,
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
    val codexPage: Int,
    val armoryPage: Int,
    val settingsPage: Int,
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
    val unlockedWeapons: ImmutableSet<WeaponId>,
    private val itemStacks: ImmutableList<Int>,
    private val discoveredItemIds: ImmutableSet<Int>,
    private val metaRanks: ImmutableList<Int>,
    private val relicRanks: ImmutableList<Int>,
) {
    val speed: Float get() = length(velocityX, velocityY)
    val rebirthProfile: RebirthProfile get() = RebirthProgression.profile(rebirthLevel)
    val nextRebirthProfile: RebirthProfile get() = RebirthProgression.profile(rebirthLevel + 1)
    val canRebirth: Boolean
        get() = rebirthLevel < RebirthProgression.MAX_LEVEL &&
            highestClearedRebirth >= rebirthLevel &&
            (phase == GamePhase.MENU || phase == GamePhase.VICTORY)
    val runProgress: Float get() = clamp(elapsed / RUN_DURATION_SECONDS, 0f, 1f)
    val tetherDistance: Float
        get() {
            val targetX = cameraX + pointerX - screenWidth * 0.5f
            val targetY = cameraY + pointerY - screenHeight * 0.5f
            return length(targetX - coreX, targetY - coreY)
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
    val choicesCanReroll: Boolean
        get() = phase == GamePhase.CHOICE && rerollsRemaining > 0 && when (choiceType) {
            ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> true
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> false
        }
    val maxCodexPage: Int get() = (ItemCatalog.ITEM_COUNT - 1) / CODEX_PAGE_SIZE
    val maxArmoryPage: Int get() = (WeaponCatalog.all.size - 1) / ARMORY_PAGE_SIZE
    val codexPageItems: ImmutableList<ItemDefinition>
        get() {
            val start = codexPage * CODEX_PAGE_SIZE
            return ItemCatalog.all
                .subList(start, min(start + CODEX_PAGE_SIZE, ItemCatalog.all.size))
                .toImmutableList()
        }
    val armoryPageWeapons: ImmutableList<WeaponDefinition>
        get() {
            val start = armoryPage * ARMORY_PAGE_SIZE
            return WeaponCatalog.all
                .subList(start, min(start + ARMORY_PAGE_SIZE, WeaponCatalog.all.size))
                .toImmutableList()
        }

    fun relicRank(id: RelicId): Int = relicRanks.getOrElse(id.ordinal) { 0 }
    fun itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }
    fun isItemDiscovered(itemId: Int): Boolean = itemId in discoveredItemIds
    fun metaLevel(id: MetaUpgradeId): Int = metaRanks.getOrElse(id.ordinal) { 0 }
    fun isWeaponUnlocked(id: WeaponId): Boolean = id in unlockedWeapons
    fun isCoreShapeUnlocked(shape: CoreShape): Boolean = lifetimeMatter >= when (shape) {
        CoreShape.ORB -> 0L
        CoreShape.PRISM -> 25L
        CoreShape.SHARD -> 90L
    }
    companion object {
        const val RUN_DURATION_SECONDS = 20f * 60f
        const val MAX_HEAT = 100f
        const val CORE_RADIUS = 16f
        const val FIXED_STEP = 1f / 120f
        const val CODEX_PAGE_SIZE = 10
        const val ARMORY_PAGE_SIZE = 3
    }
}
