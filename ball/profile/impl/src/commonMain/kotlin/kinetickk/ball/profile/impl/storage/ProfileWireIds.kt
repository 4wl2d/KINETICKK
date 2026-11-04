// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.localization.AppLanguage

internal fun CoreShape.wireId(): String = when (this) {
    CoreShape.ORB -> "ORB"
    CoreShape.PRISM -> "PRISM"
    CoreShape.SHARD -> "SHARD"
    CoreShape.RING -> "RING"
    CoreShape.DIAMOND -> "DIAMOND"
    CoreShape.TESSERACT -> "TESSERACT"
    CoreShape.STAR -> "STAR"
    CoreShape.SPIRAL -> "SPIRAL"
    CoreShape.DIPOLE -> "DIPOLE"
    CoreShape.PENTAGON -> "PENTAGON"
    CoreShape.CRESCENT -> "CRESCENT"
    CoreShape.HEXAGON -> "HEXAGON"
}

internal fun String.coreShape(): CoreShape = when (this) {
    "ORB" -> CoreShape.ORB
    "PRISM" -> CoreShape.PRISM
    "SHARD" -> CoreShape.SHARD
    "RING" -> CoreShape.RING
    "DIAMOND" -> CoreShape.DIAMOND
    "TESSERACT" -> CoreShape.TESSERACT
    "STAR" -> CoreShape.STAR
    "SPIRAL" -> CoreShape.SPIRAL
    "DIPOLE" -> CoreShape.DIPOLE
    "PENTAGON" -> CoreShape.PENTAGON
    "CRESCENT" -> CoreShape.CRESCENT
    "HEXAGON" -> CoreShape.HEXAGON
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WeaponId.wireId(): String = when (this) {
    WeaponId.FLUX_WAKE -> "FLUX_WAKE"
    WeaponId.MORNINGSTAR -> "MORNINGSTAR"
    WeaponId.PHASE_LATTICE -> "PHASE_LATTICE"
    WeaponId.NULL_LANCE -> "NULL_LANCE"
    WeaponId.GRAVITY_MINES -> "GRAVITY_MINES"
    WeaponId.ION_SWARM -> "ION_SWARM"
    WeaponId.RIFT_BLADES -> "RIFT_BLADES"
    WeaponId.ARC_COIL -> "ARC_COIL"
    WeaponId.QUASAR_CANNON -> "QUASAR_CANNON"
    WeaponId.ENTROPY_FIELD -> "ENTROPY_FIELD"
    WeaponId.SINGULARITY_SPEAR -> "SINGULARITY_SPEAR"
    WeaponId.PRISM_RELAY -> "PRISM_RELAY"
    WeaponId.GYRE_DISC -> "GYRE_DISC"
    WeaponId.SCATTER_BLOOM -> "SCATTER_BLOOM"
    WeaponId.TETHER_ARC -> "TETHER_ARC"
    WeaponId.SENTRY_GRID -> "SENTRY_GRID"
    WeaponId.HUNTER_SALVO -> "HUNTER_SALVO"
    WeaponId.CONCUSSION_ENGINE -> "CONCUSSION_ENGINE"
    WeaponId.GLACIER_BELL -> "GLACIER_BELL"
    WeaponId.EVENT_LENS -> "EVENT_LENS"
    WeaponId.SOLAR_SWEEP -> "SOLAR_SWEEP"
    WeaponId.AEGIS_SHARDS -> "AEGIS_SHARDS"
    WeaponId.HOLLOW_RIFT -> "HOLLOW_RIFT"
    WeaponId.RESONANCE_DRUM -> "RESONANCE_DRUM"
}

internal fun String.weaponId(): WeaponId = when (this) {
    "FLUX_WAKE" -> WeaponId.FLUX_WAKE
    "MORNINGSTAR" -> WeaponId.MORNINGSTAR
    "PHASE_LATTICE" -> WeaponId.PHASE_LATTICE
    "NULL_LANCE" -> WeaponId.NULL_LANCE
    "GRAVITY_MINES" -> WeaponId.GRAVITY_MINES
    "ION_SWARM" -> WeaponId.ION_SWARM
    "RIFT_BLADES" -> WeaponId.RIFT_BLADES
    "ARC_COIL" -> WeaponId.ARC_COIL
    "QUASAR_CANNON" -> WeaponId.QUASAR_CANNON
    "ENTROPY_FIELD" -> WeaponId.ENTROPY_FIELD
    "SINGULARITY_SPEAR" -> WeaponId.SINGULARITY_SPEAR
    "PRISM_RELAY" -> WeaponId.PRISM_RELAY
    "GYRE_DISC" -> WeaponId.GYRE_DISC
    "SCATTER_BLOOM" -> WeaponId.SCATTER_BLOOM
    "TETHER_ARC" -> WeaponId.TETHER_ARC
    "SENTRY_GRID" -> WeaponId.SENTRY_GRID
    "HUNTER_SALVO" -> WeaponId.HUNTER_SALVO
    "CONCUSSION_ENGINE" -> WeaponId.CONCUSSION_ENGINE
    "GLACIER_BELL" -> WeaponId.GLACIER_BELL
    "EVENT_LENS" -> WeaponId.EVENT_LENS
    "SOLAR_SWEEP" -> WeaponId.SOLAR_SWEEP
    "AEGIS_SHARDS" -> WeaponId.AEGIS_SHARDS
    "HOLLOW_RIFT" -> WeaponId.HOLLOW_RIFT
    "RESONANCE_DRUM" -> WeaponId.RESONANCE_DRUM
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Core Matrix wire ids: stable names, never renamed or reused. */
internal fun MatrixNodeId.wireId(): String = when (this) {
    MatrixNodeId.IMPACT_DRIVER -> "IMPACT_DRIVER"
    MatrixNodeId.WEAPON_CALIBRATION -> "WEAPON_CALIBRATION"
    MatrixNodeId.CRITICAL_LATTICE -> "CRITICAL_LATTICE"
    MatrixNodeId.ARMED_START -> "ARMED_START"
    MatrixNodeId.CATALYST_CORE -> "CATALYST_CORE"
    MatrixNodeId.OVERDRIVE_COIL -> "OVERDRIVE_COIL"
    MatrixNodeId.REBORN_ARSENAL -> "REBORN_ARSENAL"
    MatrixNodeId.GLASS_ENGINE -> "GLASS_ENGINE"
    MatrixNodeId.REINFORCED_HULL -> "REINFORCED_HULL"
    MatrixNodeId.HARDLIGHT_RESERVE -> "HARDLIGHT_RESERVE"
    MatrixNodeId.IMPACT_DAMPERS -> "IMPACT_DAMPERS"
    MatrixNodeId.SECOND_WIND -> "SECOND_WIND"
    MatrixNodeId.NANOFORGE -> "NANOFORGE"
    MatrixNodeId.BASTION_PROTOCOL -> "BASTION_PROTOCOL"
    MatrixNodeId.AEGIS_LATTICE -> "AEGIS_LATTICE"
    MatrixNodeId.JUGGERNAUT -> "JUGGERNAUT"
    MatrixNodeId.MAGNET_ARRAY -> "MAGNET_ARRAY"
    MatrixNodeId.CRYO_LOOP -> "CRYO_LOOP"
    MatrixNodeId.VECTOR_THRUSTERS -> "VECTOR_THRUSTERS"
    MatrixNodeId.NEAR_MISS -> "NEAR_MISS"
    MatrixNodeId.RICOCHET_RIG -> "RICOCHET_RIG"
    MatrixNodeId.OVERCLOCKED_REFLEXES -> "OVERCLOCKED_REFLEXES"
    MatrixNodeId.GRAVITY_SURFER -> "GRAVITY_SURFER"
    MatrixNodeId.PERPETUAL_DRIFT -> "PERPETUAL_DRIFT"
    MatrixNodeId.SALVAGE_PROTOCOL -> "SALVAGE_PROTOCOL"
    MatrixNodeId.DATA_ARCHIVE -> "DATA_ARCHIVE"
    MatrixNodeId.REROLL_CACHE -> "REROLL_CACHE"
    MatrixNodeId.WIDE_OFFER -> "WIDE_OFFER"
    MatrixNodeId.LUCKY_CHARM -> "LUCKY_CHARM"
    MatrixNodeId.ELITE_BOUNTY -> "ELITE_BOUNTY"
    MatrixNodeId.HEIRLOOM_VAULT -> "HEIRLOOM_VAULT"
    MatrixNodeId.PROSPECTOR -> "PROSPECTOR"
}

internal fun String.matrixNodeId(): MatrixNodeId = when (this) {
    "IMPACT_DRIVER" -> MatrixNodeId.IMPACT_DRIVER
    "WEAPON_CALIBRATION" -> MatrixNodeId.WEAPON_CALIBRATION
    "CRITICAL_LATTICE" -> MatrixNodeId.CRITICAL_LATTICE
    "ARMED_START" -> MatrixNodeId.ARMED_START
    "CATALYST_CORE" -> MatrixNodeId.CATALYST_CORE
    "OVERDRIVE_COIL" -> MatrixNodeId.OVERDRIVE_COIL
    "REBORN_ARSENAL" -> MatrixNodeId.REBORN_ARSENAL
    "GLASS_ENGINE" -> MatrixNodeId.GLASS_ENGINE
    "REINFORCED_HULL" -> MatrixNodeId.REINFORCED_HULL
    "HARDLIGHT_RESERVE" -> MatrixNodeId.HARDLIGHT_RESERVE
    "IMPACT_DAMPERS" -> MatrixNodeId.IMPACT_DAMPERS
    "SECOND_WIND" -> MatrixNodeId.SECOND_WIND
    "NANOFORGE" -> MatrixNodeId.NANOFORGE
    "BASTION_PROTOCOL" -> MatrixNodeId.BASTION_PROTOCOL
    "AEGIS_LATTICE" -> MatrixNodeId.AEGIS_LATTICE
    "JUGGERNAUT" -> MatrixNodeId.JUGGERNAUT
    "MAGNET_ARRAY" -> MatrixNodeId.MAGNET_ARRAY
    "CRYO_LOOP" -> MatrixNodeId.CRYO_LOOP
    "VECTOR_THRUSTERS" -> MatrixNodeId.VECTOR_THRUSTERS
    "NEAR_MISS" -> MatrixNodeId.NEAR_MISS
    "RICOCHET_RIG" -> MatrixNodeId.RICOCHET_RIG
    "OVERCLOCKED_REFLEXES" -> MatrixNodeId.OVERCLOCKED_REFLEXES
    "GRAVITY_SURFER" -> MatrixNodeId.GRAVITY_SURFER
    "PERPETUAL_DRIFT" -> MatrixNodeId.PERPETUAL_DRIFT
    "SALVAGE_PROTOCOL" -> MatrixNodeId.SALVAGE_PROTOCOL
    "DATA_ARCHIVE" -> MatrixNodeId.DATA_ARCHIVE
    "REROLL_CACHE" -> MatrixNodeId.REROLL_CACHE
    "WIDE_OFFER" -> MatrixNodeId.WIDE_OFFER
    "LUCKY_CHARM" -> MatrixNodeId.LUCKY_CHARM
    "ELITE_BOUNTY" -> MatrixNodeId.ELITE_BOUNTY
    "HEIRLOOM_VAULT" -> MatrixNodeId.HEIRLOOM_VAULT
    "PROSPECTOR" -> MatrixNodeId.PROSPECTOR
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Wire ids of the current Relic catalog; retired names are never mapped (see isFromRetiredCatalog). */
internal fun RelicId.wireId(): String = when (this) {
    RelicId.PERPETUAL_VECTOR -> "PERPETUAL_VECTOR"
    RelicId.BLINK_DRIVE -> "BLINK_DRIVE"
    RelicId.GYRE_ENGINE -> "GYRE_ENGINE"
    RelicId.GLASS_COMET -> "GLASS_COMET"
    RelicId.EXECUTIONERS_PRISM -> "EXECUTIONERS_PRISM"
    RelicId.ECHO_CHAMBER -> "ECHO_CHAMBER"
    RelicId.FURNACE_COVENANT -> "FURNACE_COVENANT"
    RelicId.CINDER_CROWN -> "CINDER_CROWN"
    RelicId.OVERDRIVE_CASCADE -> "OVERDRIVE_CASCADE"
    RelicId.ABSOLUTE_ZERO -> "ABSOLUTE_ZERO"
    RelicId.STASIS_ANCHOR -> "STASIS_ANCHOR"
    RelicId.STORMHEART_CAPACITOR -> "STORMHEART_CAPACITOR"
    RelicId.AEGIS_INVERSION -> "AEGIS_INVERSION"
    RelicId.NEUTRON_HEART -> "NEUTRON_HEART"
    RelicId.EVENT_HORIZON -> "EVENT_HORIZON"
    RelicId.VERDANT_ROT -> "VERDANT_ROT"
    RelicId.PANDEMIC_CODEX -> "PANDEMIC_CODEX"
    RelicId.AGONY_SCEPTER -> "AGONY_SCEPTER"
    RelicId.HOLLOW_CROWN -> "HOLLOW_CROWN"
    RelicId.OUROBOROS_SEAL -> "OUROBOROS_SEAL"
    RelicId.THUNDER_CHOIR -> "THUNDER_CHOIR"
    RelicId.NULL_COVENANT -> "NULL_COVENANT"
}

internal fun String.relicId(): RelicId = when (this) {
    "PERPETUAL_VECTOR" -> RelicId.PERPETUAL_VECTOR
    "BLINK_DRIVE" -> RelicId.BLINK_DRIVE
    "GYRE_ENGINE" -> RelicId.GYRE_ENGINE
    "GLASS_COMET" -> RelicId.GLASS_COMET
    "EXECUTIONERS_PRISM" -> RelicId.EXECUTIONERS_PRISM
    "ECHO_CHAMBER" -> RelicId.ECHO_CHAMBER
    "FURNACE_COVENANT" -> RelicId.FURNACE_COVENANT
    "CINDER_CROWN" -> RelicId.CINDER_CROWN
    "OVERDRIVE_CASCADE" -> RelicId.OVERDRIVE_CASCADE
    "ABSOLUTE_ZERO" -> RelicId.ABSOLUTE_ZERO
    "STASIS_ANCHOR" -> RelicId.STASIS_ANCHOR
    "STORMHEART_CAPACITOR" -> RelicId.STORMHEART_CAPACITOR
    "AEGIS_INVERSION" -> RelicId.AEGIS_INVERSION
    "NEUTRON_HEART" -> RelicId.NEUTRON_HEART
    "EVENT_HORIZON" -> RelicId.EVENT_HORIZON
    "VERDANT_ROT" -> RelicId.VERDANT_ROT
    "PANDEMIC_CODEX" -> RelicId.PANDEMIC_CODEX
    "AGONY_SCEPTER" -> RelicId.AGONY_SCEPTER
    "HOLLOW_CROWN" -> RelicId.HOLLOW_CROWN
    "OUROBOROS_SEAL" -> RelicId.OUROBOROS_SEAL
    "THUNDER_CHOIR" -> RelicId.THUNDER_CHOIR
    "NULL_COVENANT" -> RelicId.NULL_COVENANT
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ReactionId.wireId(): String = when (this) {
    ReactionId.THERMAL_SHOCK -> "THERMAL_SHOCK"
    ReactionId.PLASMA_BURST -> "PLASMA_BURST"
    ReactionId.FUSION -> "FUSION"
    ReactionId.WILDFIRE -> "WILDFIRE"
    ReactionId.SUPERCONDUCT -> "SUPERCONDUCT"
    ReactionId.GLACIER_CRUSH -> "GLACIER_CRUSH"
    ReactionId.EMBRITTLE -> "EMBRITTLE"
    ReactionId.MAGNETIZE -> "MAGNETIZE"
    ReactionId.FEEDBACK -> "FEEDBACK"
    ReactionId.ROT_WELL -> "ROT_WELL"
    ReactionId.THUNDERCLAP -> "THUNDERCLAP"
    ReactionId.SHATTERPOINT -> "SHATTERPOINT"
    ReactionId.RESONANT_ARC -> "RESONANT_ARC"
    ReactionId.SEISMIC -> "SEISMIC"
    ReactionId.FRACTURE -> "FRACTURE"
    ReactionId.SILENCE -> "SILENCE"
    ReactionId.BLACKFIRE -> "BLACKFIRE"
    ReactionId.STASIS -> "STASIS"
    ReactionId.ANNIHILATE -> "ANNIHILATE"
    ReactionId.NULL_WELL -> "NULL_WELL"
    ReactionId.OBLIVION -> "OBLIVION"
}

internal fun String.reactionId(): ReactionId = when (this) {
    "THERMAL_SHOCK" -> ReactionId.THERMAL_SHOCK
    "PLASMA_BURST" -> ReactionId.PLASMA_BURST
    "FUSION" -> ReactionId.FUSION
    "WILDFIRE" -> ReactionId.WILDFIRE
    "SUPERCONDUCT" -> ReactionId.SUPERCONDUCT
    "GLACIER_CRUSH" -> ReactionId.GLACIER_CRUSH
    "EMBRITTLE" -> ReactionId.EMBRITTLE
    "MAGNETIZE" -> ReactionId.MAGNETIZE
    "FEEDBACK" -> ReactionId.FEEDBACK
    "ROT_WELL" -> ReactionId.ROT_WELL
    "THUNDERCLAP" -> ReactionId.THUNDERCLAP
    "SHATTERPOINT" -> ReactionId.SHATTERPOINT
    "RESONANT_ARC" -> ReactionId.RESONANT_ARC
    "SEISMIC" -> ReactionId.SEISMIC
    "FRACTURE" -> ReactionId.FRACTURE
    "SILENCE" -> ReactionId.SILENCE
    "BLACKFIRE" -> ReactionId.BLACKFIRE
    "STASIS" -> ReactionId.STASIS
    "ANNIHILATE" -> ReactionId.ANNIHILATE
    "NULL_WELL" -> ReactionId.NULL_WELL
    "OBLIVION" -> ReactionId.OBLIVION
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun RelicPairingId.wireId(): String = when (this) {
    RelicPairingId.ENDLESS_GYRE -> "ENDLESS_GYRE"
    RelicPairingId.ASSASSINS_LINE -> "ASSASSINS_LINE"
    RelicPairingId.MELTDOWN -> "MELTDOWN"
    RelicPairingId.COLD_VERDICT -> "COLD_VERDICT"
    RelicPairingId.LIGHTNING_ROD -> "LIGHTNING_ROD"
    RelicPairingId.BLACK_STAR -> "BLACK_STAR"
    RelicPairingId.BLIGHT_GARDEN -> "BLIGHT_GARDEN"
    RelicPairingId.FIRESTORM -> "FIRESTORM"
    RelicPairingId.FORBIDDEN_ECHO -> "FORBIDDEN_ECHO"
    RelicPairingId.ROT_WARD -> "ROT_WARD"
    RelicPairingId.STILL_WATERS -> "STILL_WATERS"
    RelicPairingId.FOLD_VECTOR -> "FOLD_VECTOR"
    RelicPairingId.HOLLOW_VOW -> "HOLLOW_VOW"
    RelicPairingId.FARADAY_CAGE -> "FARADAY_CAGE"
    RelicPairingId.THERMAL_PARADOX -> "THERMAL_PARADOX"
    RelicPairingId.EXTINGUISHED -> "EXTINGUISHED"
    RelicPairingId.SHATTERED_AEGIS -> "SHATTERED_AEGIS"
    RelicPairingId.CONTRADICTION -> "CONTRADICTION"
    RelicPairingId.HEAVY_BLINK -> "HEAVY_BLINK"
    RelicPairingId.FRAGILE_REBIRTH -> "FRAGILE_REBIRTH"
}

internal fun String.relicPairingId(): RelicPairingId = when (this) {
    "ENDLESS_GYRE" -> RelicPairingId.ENDLESS_GYRE
    "ASSASSINS_LINE" -> RelicPairingId.ASSASSINS_LINE
    "MELTDOWN" -> RelicPairingId.MELTDOWN
    "COLD_VERDICT" -> RelicPairingId.COLD_VERDICT
    "LIGHTNING_ROD" -> RelicPairingId.LIGHTNING_ROD
    "BLACK_STAR" -> RelicPairingId.BLACK_STAR
    "BLIGHT_GARDEN" -> RelicPairingId.BLIGHT_GARDEN
    "FIRESTORM" -> RelicPairingId.FIRESTORM
    "FORBIDDEN_ECHO" -> RelicPairingId.FORBIDDEN_ECHO
    "ROT_WARD" -> RelicPairingId.ROT_WARD
    "STILL_WATERS" -> RelicPairingId.STILL_WATERS
    "FOLD_VECTOR" -> RelicPairingId.FOLD_VECTOR
    "HOLLOW_VOW" -> RelicPairingId.HOLLOW_VOW
    "FARADAY_CAGE" -> RelicPairingId.FARADAY_CAGE
    "THERMAL_PARADOX" -> RelicPairingId.THERMAL_PARADOX
    "EXTINGUISHED" -> RelicPairingId.EXTINGUISHED
    "SHATTERED_AEGIS" -> RelicPairingId.SHATTERED_AEGIS
    "CONTRADICTION" -> RelicPairingId.CONTRADICTION
    "HEAVY_BLINK" -> RelicPairingId.HEAVY_BLINK
    "FRAGILE_REBIRTH" -> RelicPairingId.FRAGILE_REBIRTH
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ParticleDensity.wireId(): String = when (this) {
    ParticleDensity.LOW -> "LOW"
    ParticleDensity.NORMAL -> "NORMAL"
    ParticleDensity.HIGH -> "HIGH"
}

internal fun String.particleDensity(): ParticleDensity = when (this) {
    "LOW" -> ParticleDensity.LOW
    "NORMAL" -> ParticleDensity.NORMAL
    "HIGH" -> ParticleDensity.HIGH
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun DamageNumberSize.wireId(): String = when (this) {
    DamageNumberSize.SMALL -> "SMALL"
    DamageNumberSize.NORMAL -> "NORMAL"
    DamageNumberSize.LARGE -> "LARGE"
    DamageNumberSize.HUGE -> "HUGE"
}

internal fun String.damageNumberSize(): DamageNumberSize = when (this) {
    "SMALL" -> DamageNumberSize.SMALL
    "NORMAL" -> DamageNumberSize.NORMAL
    "LARGE" -> DamageNumberSize.LARGE
    "HUGE" -> DamageNumberSize.HUGE
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun DamageNumberFormat.wireId(): String = when (this) {
    DamageNumberFormat.COMPACT -> "COMPACT"
    DamageNumberFormat.FULL -> "FULL"
}

internal fun String.damageNumberFormat(): DamageNumberFormat = when (this) {
    "COMPACT" -> DamageNumberFormat.COMPACT
    "FULL" -> DamageNumberFormat.FULL
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** DEFAULT has no wire id: it is represented only by the absent field. */
internal fun ColorVision.wireIdOrNull(): String? = when (this) {
    ColorVision.DEFAULT -> null
    ColorVision.PROTAN -> "PROTAN"
    ColorVision.DEUTAN -> "DEUTAN"
    ColorVision.TRITAN -> "TRITAN"
    ColorVision.MONO -> "MONO"
}

internal fun String.colorVision(): ColorVision = when (this) {
    "PROTAN" -> ColorVision.PROTAN
    "DEUTAN" -> ColorVision.DEUTAN
    "TRITAN" -> ColorVision.TRITAN
    "MONO" -> ColorVision.MONO
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun String.appLanguage(): AppLanguage = when (this) {
    "ru" -> AppLanguage.Russian
    "en" -> AppLanguage.English
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WindowMode.wireId(): String = when (this) {
    WindowMode.WINDOWED -> "WINDOWED"
    WindowMode.BORDERLESS -> "BORDERLESS"
    WindowMode.FULLSCREEN -> "FULLSCREEN"
}

internal fun String.windowMode(): WindowMode = when (this) {
    "WINDOWED" -> WindowMode.WINDOWED
    "BORDERLESS" -> WindowMode.BORDERLESS
    "FULLSCREEN" -> WindowMode.FULLSCREEN
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun WindowSizePreset.wireId(): String = when (this) {
    WindowSizePreset.W1280X720 -> "W1280X720"
    WindowSizePreset.W1280X800 -> "W1280X800"
    WindowSizePreset.W1366X768 -> "W1366X768"
    WindowSizePreset.W1440X900 -> "W1440X900"
    WindowSizePreset.W1600X900 -> "W1600X900"
    WindowSizePreset.W1920X1080 -> "W1920X1080"
    WindowSizePreset.W2560X1440 -> "W2560X1440"
}

internal fun String.windowSizePreset(): WindowSizePreset = when (this) {
    "W1280X720" -> WindowSizePreset.W1280X720
    "W1280X800" -> WindowSizePreset.W1280X800
    "W1366X768" -> WindowSizePreset.W1366X768
    "W1440X900" -> WindowSizePreset.W1440X900
    "W1600X900" -> WindowSizePreset.W1600X900
    "W1920X1080" -> WindowSizePreset.W1920X1080
    "W2560X1440" -> WindowSizePreset.W2560X1440
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

internal fun ReactionEffectsLevel.wireId(): String = when (this) {
    ReactionEffectsLevel.FULL -> "FULL"
    ReactionEffectsLevel.REDUCED -> "REDUCED"
    ReactionEffectsLevel.MINIMAL -> "MINIMAL"
}

internal fun String.reactionEffectsLevel(): ReactionEffectsLevel = when (this) {
    "FULL" -> ReactionEffectsLevel.FULL
    "REDUCED" -> ReactionEffectsLevel.REDUCED
    "MINIMAL" -> ReactionEffectsLevel.MINIMAL
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}

/** Rebindable action wire ids: stable names, never renamed or reused. Keys use [BindableKey.wireId]. */
internal fun GameAction.wireId(): String = when (this) {
    GameAction.DASH -> "DASH"
    GameAction.BRAKE -> "BRAKE"
    GameAction.SKILL_1 -> "SKILL_1"
    GameAction.SKILL_2 -> "SKILL_2"
    GameAction.SKILL_3 -> "SKILL_3"
    GameAction.APEX -> "APEX"
    GameAction.PAUSE -> "PAUSE"
    GameAction.REROLL -> "REROLL"
    GameAction.RESTART -> "RESTART"
    GameAction.CHOICE_1 -> "CHOICE_1"
    GameAction.CHOICE_2 -> "CHOICE_2"
    GameAction.CHOICE_3 -> "CHOICE_3"
    GameAction.CHOICE_4 -> "CHOICE_4"
    GameAction.PERFORMANCE_HUD -> "PERFORMANCE_HUD"
}

internal fun String.gameAction(): GameAction = when (this) {
    "DASH" -> GameAction.DASH
    "BRAKE" -> GameAction.BRAKE
    "SKILL_1" -> GameAction.SKILL_1
    "SKILL_2" -> GameAction.SKILL_2
    "SKILL_3" -> GameAction.SKILL_3
    "APEX" -> GameAction.APEX
    "PAUSE" -> GameAction.PAUSE
    "REROLL" -> GameAction.REROLL
    "RESTART" -> GameAction.RESTART
    "CHOICE_1" -> GameAction.CHOICE_1
    "CHOICE_2" -> GameAction.CHOICE_2
    "CHOICE_3" -> GameAction.CHOICE_3
    "CHOICE_4" -> GameAction.CHOICE_4
    "PERFORMANCE_HUD" -> GameAction.PERFORMANCE_HUD
    else -> reject(ProfileSnapshotRejection.INVALID_STABLE_ID)
}
