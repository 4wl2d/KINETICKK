// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.renderModel

import kinetickk.ball.content.api.*

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.ImmutableSet
import kinetickk.ball.gameplay.nucleus.model.ChoiceOption
import kinetickk.ball.gameplay.nucleus.model.ChoiceType
import kinetickk.ball.gameplay.nucleus.model.clamp
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.gameplay.nucleus.model.EnemyType
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.gameplay.nucleus.model.GamePhase
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.gameplay.nucleus.model.length
import kinetickk.ball.gameplay.nucleus.model.PickupType
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.gameplay.nucleus.model.WeaponNodeType

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

/**
 * Immutable render payload. No mutable simulation container crosses this boundary.
 */
class GameplayRenderModel internal constructor(
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
    private val itemStacks: ImmutableList<Int>,
    private val discoveredItemIds: ImmutableSet<Int>,
    private val relicRanks: ImmutableList<Int>,
) {
    val speed: Float get() = length(velocityX, velocityY)
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
    val choicesCanReroll: Boolean
        get() = phase == GamePhase.CHOICE && rerollsRemaining > 0 && when (choiceType) {
            ChoiceType.ITEM, ChoiceType.WEAPON, ChoiceType.RELIC -> true
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND -> false
        }
    val itemStacksSnapshot: ImmutableList<Int> get() = itemStacks
    fun relicRank(id: RelicId): Int = relicRanks.getOrElse(id.ordinal) { 0 }
    fun itemStack(itemId: Int): Int = itemStacks.getOrElse(itemId) { 0 }
    companion object {
        const val RUN_DURATION_SECONDS = 20f * 60f
        const val MAX_HEAT = 100f
        const val CORE_RADIUS = 16f
        const val FIXED_STEP = 1f / 120f
    }
}
