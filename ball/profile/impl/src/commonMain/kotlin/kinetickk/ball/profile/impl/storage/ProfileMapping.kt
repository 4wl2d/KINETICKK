// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl.storage

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.ReactionId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.DEFAULT_INTERFACE_SCALE_PERCENT
import kinetickk.ball.profile.api.GameAction
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotRejection
import kinetickk.ball.profile.api.REFRESH_RATE_AUTO
import kinetickk.ball.profile.api.ReactionEffectsLevel
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.WindowMode
import kinetickk.ball.profile.api.WindowSizePreset
import kinetickk.foundation.common.localization.AppLanguage

internal fun ProfileSnapshot.toDto(): ProfileSnapshotDto {
    validateProfile(profile)
    rejectUnless(revision.value >= 0L, ProfileSnapshotRejection.INVALID_DECIMAL)

    val preferences = profile.preferences
    val matrixRanks = MatrixNodeId.entries
        .filter { id -> profile.matrix.rank(id) > 0 }
        .map { id -> MatrixNodeRankDto(id = id.wireId(), rank = profile.matrix.rank(id)) }
        .sortedBy(MatrixNodeRankDto::id)

    return ProfileSnapshotDto(
        revision = revision.value.toString(),
        profile = PlayerProfileDto(
            preferences = PlayerPreferencesDto(
                soundEnabled = preferences.soundEnabled,
                musicEnabled = preferences.musicEnabled,
                masterVolumePercent = preferences.masterVolume.toPercent(),
                simulationSpeedPercent = preferences.simulationSpeed.toPercent(),
                textScalePercent = preferences.textScale.toPercent(),
                screenShake = preferences.screenShake,
                particleDensityId = preferences.particleDensity.wireId(),
                damageNumbers = preferences.damageNumbers,
                damageNumberSizeId = preferences.damageNumberSize.wireId(),
                damageNumberFormatId = preferences.damageNumberFormat.wireId(),
                damageNumberTierThreshold = preferences.damageNumberTierThreshold,
                languageCode = preferences.language.code,
                runStatisticsOnLeft = preferences.runStatisticsOnLeft,
                colorVisionId = preferences.colorVision.wireIdOrNull(),
                minimapEnabled = if (preferences.minimapEnabled) null else false,
                windowModeId = preferences.windowMode.takeUnless { it == WindowMode.WINDOWED }?.wireId(),
                windowSizeId = preferences.windowSize.takeUnless { it == WindowSizePreset.W1280X800 }?.wireId(),
                vsyncEnabled = if (preferences.vsync) null else false,
                frameRateCap = preferences.frameRateCap.takeUnless { it == 0 },
                refreshRateHz = preferences.refreshRateHz.takeUnless { it == REFRESH_RATE_AUTO },
                immersiveEnabled = if (preferences.immersive) true else null,
                interfaceScalePercent = preferences.interfaceScalePercent.takeUnless { it == DEFAULT_INTERFACE_SCALE_PERCENT },
                showFps = if (preferences.showFps) true else null,
                pauseOnFocusLoss = if (preferences.pauseOnFocusLoss) null else false,
                reactionEffectsId = preferences.reactionEffects.takeUnless { it == ReactionEffectsLevel.FULL }?.wireId(),
                keyBindings = GameAction.entries
                    .filter { action -> preferences.keyBindings.key(action) != KeyBindings.DEFAULT.key(action) }
                    .map { action -> KeyBindingDto(action = action.wireId(), key = preferences.keyBindings.key(action).wireId) }
                    .sortedBy(KeyBindingDto::action),
            ),
            economy = PlayerEconomyDto(
                matter = profile.economy.matter.toString(),
                lifetimeMatter = profile.economy.lifetimeMatter.toString(),
            ),
            loadout = PlayerLoadoutDto(
                coreShapeId = profile.loadout.coreShape.wireId(),
                selectedWeaponId = profile.loadout.selectedWeapon.wireId(),
                unlockedWeaponIds = profile.loadout.unlockedWeapons
                    .map(WeaponId::wireId)
                    .sorted(),
            ),
            matrix = MatrixProgressDto(matrixRanks),
            collection = PlayerCollectionDto(
                itemCatalogEpoch = ContentBounds.COLLECTION_GENERATION,
                discoveredItemIds = profile.collection.discoveredItemIds.sorted(),
                newItemIds = profile.collection.newItemIds.sorted(),
                discoveredRelicIds = profile.collection.discoveredRelicIds.map(RelicId::wireId).sorted(),
                newRelicIds = profile.collection.newRelicIds.map(RelicId::wireId).sorted(),
                discoveredReactionIds = profile.collection.discoveredReactionIds.map(ReactionId::wireId).sorted(),
                discoveredPairingIds = profile.collection.discoveredPairingIds.map(RelicPairingId::wireId).sorted(),
                heirloomRelicIds = profile.collection.heirloomRelicIds.map(RelicId::wireId),
                recentItemIds = profile.collection.recentItemIds,
                worldDiscoveryMask = profile.collection.discoveredWorldEntries.fold(0) { mask, id -> mask or id.bit },
            ),
            characterAchievements = CharacterAchievementProgressDto(
                eliteKills = profile.characterAchievements.eliteKills.toString(),
                dashHits = profile.characterAchievements.dashHits.toString(),
                completedOrbits = profile.characterAchievements.completedOrbits.toString(),
                architectVictories = profile.characterAchievements.architectVictories.toString(),
                victoriousCharacterIds = profile.characterAchievements.victoriousCharacters.map(CoreShape::wireId).sorted(),
                totalKills = profile.characterAchievements.totalKills.toString(),
                ricochets = profile.characterAchievements.ricochets.toString(),
                dataPickups = profile.characterAchievements.dataPickups.toString(),
            ),
            rebirthProgress = RebirthProgressDto(
                level = profile.rebirthProgress.level,
                highestCleared = profile.rebirthProgress.highestCleared,
                ascendedCharacterIds = profile.rebirthProgress.ascendedCharacters.map(CoreShape::wireId).sorted(),
                characterClears = CHARACTER_SLOTS.map { profile.rebirthProgress.characterClears[it.ordinal] + 1 }.toPositionalDigits(),
            ),
            upgrades = PermanentUpgradesDto(
                c = CHARACTER_SLOTS.map(profile.upgrades::characterLevel).toPositionalDigits(),
                w = WEAPON_UPGRADE_SLOTS.map(profile.upgrades::weaponLevel).toPositionalDigits(),
            ),
        ),
    )
}

internal fun ProfileSnapshotDto.toSnapshot(): ProfileSnapshot {
    val revisionValue = revision.parseCanonicalNonNegativeLong()
    val retiredCatalog = profile.collection.isFromRetiredCatalog()

    val legacyLab = profile.labProgress
    rejectUnless(legacyLab == null || profile.matrix.ranks.isEmpty(), ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    // Upgrades were introduced long after the Lab was retired, so no save carries both.
    rejectUnless(legacyLab == null || profile.upgrades == PermanentUpgradesDto(), ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    val labRefund = legacyLab?.let(RetiredLabRefund::refund) ?: 0L
    val matrixRanks = profile.matrix.toRanks()
    val upgrades = profile.upgrades.toUpgrades()
    rejectUnless(
        profile.loadout.unlockedWeaponIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )
    rejectUnless(
        profile.collection.discoveredItemIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    rejectUnless(
        profile.characterAchievements.victoriousCharacterIds.isStrictlySorted() &&
            profile.rebirthProgress.ascendedCharacterIds.isStrictlySorted(),
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    rejectUnless(
        profile.collection.newItemIds.isStrictlySorted() &&
            profile.collection.discoveredRelicIds.isStrictlySorted() &&
            profile.collection.newRelicIds.isStrictlySorted() &&
            profile.collection.discoveredReactionIds.isStrictlySorted() &&
            profile.collection.discoveredPairingIds.isStrictlySorted() &&
            profile.collection.heirloomRelicIds.toSet().size == profile.collection.heirloomRelicIds.size,
        ProfileSnapshotRejection.INVALID_ORDER_OR_DUPLICATE,
    )

    val storedMatter = profile.economy.matter.parseCanonicalNonNegativeLong()
    val storedLifetimeMatter = profile.economy.lifetimeMatter.parseCanonicalNonNegativeLong()
    rejectUnless(storedLifetimeMatter >= storedMatter, ProfileSnapshotRejection.INCONSISTENT_PROFILE)
    // A Lab refund returns the whole spend; lifetime Matter never falls below the balance it produced.
    val matter = saturatedAdd(storedMatter, labRefund)
    val lifetimeMatter = maxOf(storedLifetimeMatter, matter)

    val decoded = PlayerProfile(
        preferences = PlayerPreferences(
            soundEnabled = profile.preferences.soundEnabled,
            musicEnabled = profile.preferences.musicEnabled,
            masterVolume = profile.preferences.masterVolumePercent / 100f,
            simulationSpeed = profile.preferences.simulationSpeedPercent / 100f,
            textScale = profile.preferences.textScalePercent / 100f,
            screenShake = profile.preferences.screenShake,
            particleDensity = profile.preferences.particleDensityId.particleDensity(),
            damageNumbers = profile.preferences.damageNumbers,
            damageNumberSize = profile.preferences.damageNumberSizeId.damageNumberSize(),
            damageNumberFormat = profile.preferences.damageNumberFormatId.damageNumberFormat(),
            damageNumberTierThreshold = profile.preferences.damageNumberTierThreshold,
            language = profile.preferences.languageCode?.appLanguage() ?: AppLanguage.Russian,
            runStatisticsOnLeft = profile.preferences.runStatisticsOnLeft ?: false,
            colorVision = profile.preferences.colorVisionId?.colorVision() ?: ColorVision.DEFAULT,
            // Only "off" is ever written; an explicit default is not a value this codec produces.
            minimapEnabled = when (profile.preferences.minimapEnabled) {
                null -> true
                false -> false
                true -> reject(ProfileSnapshotRejection.VALUE_OUT_OF_RANGE)
            },
            windowMode = profile.preferences.windowModeId?.windowMode().storedOr(WindowMode.WINDOWED),
            windowSize = profile.preferences.windowSizeId?.windowSizePreset().storedOr(WindowSizePreset.W1280X800),
            vsync = profile.preferences.vsyncEnabled.storedOr(true),
            frameRateCap = profile.preferences.frameRateCap.storedOr(0),
            refreshRateHz = profile.preferences.refreshRateHz.storedOr(REFRESH_RATE_AUTO),
            immersive = profile.preferences.immersiveEnabled.storedOr(false),
            interfaceScalePercent = profile.preferences.interfaceScalePercent.storedOr(DEFAULT_INTERFACE_SCALE_PERCENT),
            showFps = profile.preferences.showFps.storedOr(false),
            pauseOnFocusLoss = profile.preferences.pauseOnFocusLoss.storedOr(true),
            reactionEffects = profile.preferences.reactionEffectsId?.reactionEffectsLevel().storedOr(ReactionEffectsLevel.FULL),
            keyBindings = profile.preferences.keyBindings.toKeyBindings(),
        ),
        economy = PlayerEconomy(matter = matter, lifetimeMatter = lifetimeMatter),
        loadout = PlayerLoadout(
            coreShape = profile.loadout.coreShapeId.coreShape(),
            selectedWeapon = profile.loadout.selectedWeaponId.weaponId(),
            unlockedWeapons = profile.loadout.unlockedWeaponIds.mapTo(mutableSetOf()) { it.weaponId() },
        ),
        matrix = MatrixProgress(matrixRanks),
        collection = if (retiredCatalog) PlayerCollection() else profile.collection.toCollection(),
        characterAchievements = CharacterAchievementProgress(
            eliteKills = profile.characterAchievements.eliteKills.parseCanonicalNonNegativeLong(),
            dashHits = profile.characterAchievements.dashHits.parseCanonicalNonNegativeLong(),
            completedOrbits = profile.characterAchievements.completedOrbits.parseCanonicalNonNegativeLong(),
            architectVictories = profile.characterAchievements.architectVictories.parseCanonicalNonNegativeLong(),
            victoriousCharacters = profile.characterAchievements.victoriousCharacterIds.map { it.coreShape() }.toImmutableSet(),
            totalKills = profile.characterAchievements.totalKills.parseCanonicalNonNegativeLong(),
            ricochets = profile.characterAchievements.ricochets.parseCanonicalNonNegativeLong(),
            dataPickups = profile.characterAchievements.dataPickups.parseCanonicalNonNegativeLong(),
        ),
        rebirthProgress = RebirthProgress(
            level = profile.rebirthProgress.level,
            highestCleared = profile.rebirthProgress.highestCleared,
            ascendedCharacters = profile.rebirthProgress.ascendedCharacterIds.map { it.coreShape() }.toImmutableSet(),
            characterClears = profile.rebirthProgress.characterClears.toCharacterClears().toImmutableList(),
        ),
        upgrades = upgrades,
    )
    validateProfile(decoded)

    return ProfileSnapshot(
        revision = ProfileRevision(revisionValue),
        profile = decoded,
    )
}

private fun PlayerCollectionDto.toCollection(): PlayerCollection = PlayerCollection(
    discoveredItemIds = discoveredItemIds.toImmutableSet(),
    newItemIds = newItemIds.toImmutableSet(),
    discoveredRelicIds = discoveredRelicIds.map { it.relicId() }.toImmutableSet(),
    newRelicIds = newRelicIds.map { it.relicId() }.toImmutableSet(),
    discoveredReactionIds = discoveredReactionIds.map { it.reactionId() }.toImmutableSet(),
    discoveredPairingIds = discoveredPairingIds.map { it.relicPairingId() }.toImmutableSet(),
    heirloomRelicIds = heirloomRelicIds.map { it.relicId() }.toImmutableList(),
    recentItemIds = recentItemIds.toImmutableList(),
    discoveredWorldEntries = WorldDiscovery.entries.filter { worldDiscoveryMask and it.bit != 0 }.toImmutableSet(),
)
