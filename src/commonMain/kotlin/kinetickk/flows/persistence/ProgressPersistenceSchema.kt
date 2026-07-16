// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flows.persistence

/**
 * Stable capture and wire contract owned by the ProgressPersistence Flow.
 *
 * These primitive codes describe the combined-save compatibility format. They do not transfer
 * Profile or Settings authority to this Flow. Assembly must map explicit reads from those
 * authorities into this contract and retain their exact consistency stamps as provenance.
 */
object ProgressPersistenceSchema {
    const val CAPTURE_SCHEMA_VERSION = 1

    const val LEGACY_PAYLOAD_VERSION = 2
    const val CURRENT_PAYLOAD_VERSION = 3
    const val LEGACY_PAYLOAD_FIELD_COUNT = 9
    const val CURRENT_PAYLOAD_FIELD_COUNT = 11

    const val CORE_SHAPE_ORB_CODE = 0
    const val CORE_SHAPE_PRISM_CODE = 1
    const val CORE_SHAPE_SHARD_CODE = 2
    const val CORE_SHAPE_CODE_COUNT = 3

    const val WEAPON_FLUX_WAKE_CODE = 0
    const val WEAPON_MORNINGSTAR_CODE = 1
    const val WEAPON_PHASE_LATTICE_CODE = 2
    const val WEAPON_NULL_LANCE_CODE = 3
    const val WEAPON_GRAVITY_MINES_CODE = 4
    const val WEAPON_ION_SWARM_CODE = 5
    const val WEAPON_RIFT_BLADES_CODE = 6
    const val WEAPON_ARC_COIL_CODE = 7
    const val WEAPON_QUASAR_CANNON_CODE = 8
    const val WEAPON_ENTROPY_FIELD_CODE = 9
    const val WEAPON_SINGULARITY_SPEAR_CODE = 10
    const val WEAPON_PRISM_RELAY_CODE = 11
    const val WEAPON_CODE_COUNT = 12
    const val BASELINE_WEAPON_CODE = WEAPON_FLUX_WAKE_CODE

    const val META_UPGRADE_CORE_INTEGRITY_CODE = 0
    const val META_UPGRADE_KINETIC_AMPLIFIER_CODE = 1
    const val META_UPGRADE_MAGNETIC_RESONANCE_CODE = 2
    const val META_UPGRADE_CRYO_VENTS_CODE = 3
    const val META_UPGRADE_DASH_CAPACITOR_CODE = 4
    const val META_UPGRADE_SALVAGE_PROTOCOL_CODE = 5
    const val META_UPGRADE_DATA_ARCHIVE_CODE = 6
    const val META_UPGRADE_ARMORY_LICENSE_CODE = 7
    const val META_UPGRADE_CODE_COUNT = 8

    const val ITEM_ID_COUNT = 400
    const val MAX_REBIRTH_LEVEL = 10

    const val LEGACY_SETTINGS_FIELD_COUNT = 7
    const val LEGACY_SETTINGS_WITH_TEXT_SCALE_FIELD_COUNT = 8
    const val CURRENT_PAYLOAD_LEGACY_SETTINGS_FIELD_COUNT = 8
    const val CURRENT_SETTINGS_FIELD_COUNT = 11

    const val PARTICLE_DENSITY_LOW_CODE = 0
    const val PARTICLE_DENSITY_NORMAL_CODE = 1
    const val PARTICLE_DENSITY_HIGH_CODE = 2
    const val PARTICLE_DENSITY_CODE_COUNT = 3

    const val DAMAGE_NUMBER_SIZE_SMALL_CODE = 0
    const val DAMAGE_NUMBER_SIZE_NORMAL_CODE = 1
    const val DAMAGE_NUMBER_SIZE_LARGE_CODE = 2
    const val DAMAGE_NUMBER_SIZE_HUGE_CODE = 3
    const val DAMAGE_NUMBER_SIZE_CODE_COUNT = 4

    const val DAMAGE_NUMBER_FORMAT_COMPACT_CODE = 0
    const val DAMAGE_NUMBER_FORMAT_FULL_CODE = 1
    const val DAMAGE_NUMBER_FORMAT_CODE_COUNT = 2

    const val DEFAULT_MASTER_VOLUME = 0.65f
    const val DEFAULT_SIMULATION_SPEED = 1.15f
    const val DEFAULT_TEXT_SCALE = 1.25f
    const val MIN_MASTER_VOLUME = 0f
    const val MAX_MASTER_VOLUME = 1f
    const val MIN_SIMULATION_SPEED = 0.75f
    const val MAX_SIMULATION_SPEED = 2f
    const val MIN_TEXT_SCALE = 1f
    const val MAX_TEXT_SCALE = 1.75f

    const val DEFAULT_DAMAGE_NUMBER_TIER_THRESHOLD = 50
    const val MIN_DAMAGE_NUMBER_TIER_THRESHOLD = 10
    const val MAX_DAMAGE_NUMBER_TIER_THRESHOLD = 100_000_000

    fun isSupportedCoreShapeCode(code: Int): Boolean = code in 0 until CORE_SHAPE_CODE_COUNT

    fun isSupportedWeaponCode(code: Int): Boolean = code in 0 until WEAPON_CODE_COUNT

    fun maxMetaUpgradeRank(code: Int): Int? = when (code) {
        META_UPGRADE_CORE_INTEGRITY_CODE -> 10
        META_UPGRADE_KINETIC_AMPLIFIER_CODE -> 10
        META_UPGRADE_MAGNETIC_RESONANCE_CODE -> 8
        META_UPGRADE_CRYO_VENTS_CODE -> 8
        META_UPGRADE_DASH_CAPACITOR_CODE -> 8
        META_UPGRADE_SALVAGE_PROTOCOL_CODE -> 10
        META_UPGRADE_DATA_ARCHIVE_CODE -> 10
        META_UPGRADE_ARMORY_LICENSE_CODE -> 12
        else -> null
    }

    fun isSupportedItemId(itemId: Int): Boolean = itemId in 0 until ITEM_ID_COUNT

    fun isSupportedParticleDensityCode(code: Int): Boolean =
        code in 0 until PARTICLE_DENSITY_CODE_COUNT

    fun isSupportedDamageNumberSizeCode(code: Int): Boolean =
        code in 0 until DAMAGE_NUMBER_SIZE_CODE_COUNT

    fun isSupportedDamageNumberFormatCode(code: Int): Boolean =
        code in 0 until DAMAGE_NUMBER_FORMAT_CODE_COUNT

    fun isSupportedDamageNumberTierThreshold(value: Int): Boolean = when (value) {
        10,
        25,
        50,
        100,
        250,
        500,
        1_000,
        2_500,
        5_000,
        10_000,
        25_000,
        50_000,
        100_000,
        250_000,
        500_000,
        1_000_000,
        2_500_000,
        5_000_000,
        10_000_000,
        25_000_000,
        50_000_000,
        100_000_000,
        -> true
        else -> false
    }
}
