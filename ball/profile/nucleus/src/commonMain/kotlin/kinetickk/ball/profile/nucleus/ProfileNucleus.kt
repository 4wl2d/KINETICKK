// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.CollectionEntry
import kinetickk.ball.profile.api.DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.HangarProjection
import kinetickk.ball.profile.api.HomeProgressProjection
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.api.MatrixProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.MatrixProgressProjection
import kinetickk.ball.profile.api.LoadoutProfileSnapshot
import kinetickk.ball.profile.api.LoadoutProjection
import kinetickk.ball.profile.api.MAX_HEIRLOOM_RELICS
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PersistenceStatusProjection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.PreferenceAdjustmentDirection
import kinetickk.ball.profile.api.PreferencesProjection
import kinetickk.ball.profile.api.ProfileBootstrapStatus
import kinetickk.ball.profile.api.ProfileEffectRef
import kinetickk.ball.profile.api.CharacterAchievementProgress
import kinetickk.ball.profile.api.ProfileGameplayProgressRejection
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfilePreferenceAdjustment
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileRunBootstrapResult
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileCoreShapeSelected
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProfileSnapshot
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.api.RebirthProgressProjection
import kinetickk.ball.profile.api.ProfileRebirthTierSelected
import kinetickk.ball.profile.api.ProfileProgressApplied
import kinetickk.ball.profile.api.RunBootstrapProjection
import kinetickk.ball.profile.api.SIMULATION_SPEED_OPTIONS
import kinetickk.ball.profile.api.isAscendant
import kinetickk.ball.profile.api.rank
import kinetickk.ball.profile.api.rankWithin
import kinetickk.foundation.collections.toImmutableSet
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure, deterministic authority for every Profile decision and projection. */
object ProfileNucleus {
    fun decide(
        state: ProfileState,
        pulse: ProfileNucleusPulse,
    ): ProfileDecision {
        return when (pulse) {
            is ProfileNucleusPulse.Intent -> {
                mutationGate(state)?.let { return rejected(it) }
                decideMutation(state, pulse.intent)
            }
            ProfileNucleusPulse.ToggleMute -> {
                mutationGate(state)?.let { return rejected(it) }
                toggleMute(state)
            }
            is ProfileNucleusPulse.SelectCoreShape -> {
                mutationGate(state)?.let { return rejected(it) }
                selectCoreShape(state, pulse.shape)
            }
            is ProfileNucleusPulse.SelectRebirthTier -> {
                mutationGate(state)?.let { return rejected(it) }
                selectRebirthTier(state, pulse.tier)
            }
            is ProfileNucleusPulse.ApplyGameplayProgress -> {
                mutationGate(state)?.let { return rejected(it) }
                applyGameplayProgress(state, pulse.update)
            }
            is ProfileNucleusPulse.WriteCompleted ->
                decideWrite(state, pulse.effectRef, pulse.result)
        }
    }

    fun query(state: ProfileState, query: ProfileQuery.GetRunBootstrap): RunBootstrapProjection =
        RunBootstrapProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            result = if (state.bootstrap == ProfileBootstrapStatus.Ready) {
                ProfileRunBootstrapResult.Ready(state.profile.toGameplaySnapshot())
            } else {
                ProfileRunBootstrapResult.Unavailable(state.bootstrap)
            },
        )

    fun query(state: ProfileState, query: ProfileQuery.GetPreferences): PreferencesProjection =
        PreferencesProjection(state.instanceId, state.revision, state.profile.preferences)

    fun query(state: ProfileState, query: ProfileQuery.GetHomeProgress): HomeProgressProjection =
        HomeProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            economy = state.profile.economy,
            loadout = state.profile.loadout,
            collection = state.profile.collection,
            rebirthProgress = state.profile.rebirthProgress,
            canAdvanceRebirth = canAdvanceRebirth(state),
            characterAchievements = state.profile.characterAchievements,
            unlockedCoreShapes = unlockedCoreShapes(state.profile, state.policy),
            characterProgress = hangarShapeProgress(state.profile, state.policy),
            workshopAffordable = isWorkshopAffordable(state.profile, state.policy),
            upgradeLevels = state.profile.upgrades.totalLevels,
        )

    fun query(state: ProfileState, query: ProfileQuery.GetMatrixProgress): MatrixProgressProjection =
        MatrixProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = MatrixProfileSnapshot(
                economy = state.profile.economy,
                progress = state.profile.matrix,
                rebirthRank = state.profile.rebirthProgress.rank,
            ),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetLoadout): LoadoutProjection =
        LoadoutProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = LoadoutProfileSnapshot(
                economy = state.profile.economy,
                loadout = state.profile.loadout,
                rebirthRank = state.profile.rebirthProgress.rank,
                upgrades = state.profile.upgrades,
            ),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetCollection): CollectionProjection =
        CollectionProjection(state.instanceId, state.revision, state.profile.collection)

    fun query(state: ProfileState, query: ProfileQuery.GetRebirthProgress): RebirthProgressProjection =
        RebirthProgressProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            snapshot = RebirthProfileSnapshot(state.profile.rebirthProgress),
            canAdvance = canAdvanceRebirth(state),
        )

    fun query(state: ProfileState, query: ProfileQuery.GetHangar): HangarProjection =
        HangarProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            shapes = hangarShapeProgress(state.profile, state.policy),
            selected = state.profile.loadout.coreShape,
            rebirthRank = state.profile.rebirthProgress.rank,
            ascendedCharacters = state.profile.rebirthProgress.ascendedCharacters,
            ascendant = state.profile.rebirthProgress.isAscendant,
        )

    fun query(state: ProfileState, query: ProfileQuery.GetPersistenceStatus): PersistenceStatusProjection =
        PersistenceStatusProjection(
            instanceId = state.instanceId,
            revision = state.revision,
            bootstrap = state.bootstrap,
            persistence = state.persistence,
        )

    private fun decideMutation(
        state: ProfileState,
        pulse: ProfilePulse.Business,
    ): ProfileDecision = when (pulse) {
        is ProfilePulse.AdjustPreference -> adjustPreference(state, pulse.adjustment)
        is ProfilePulse.PurchaseMatrixNode -> purchaseMatrixNode(state, pulse.id)
        ProfilePulse.ResetMatrix -> resetMatrix(state)
        is ProfilePulse.PurchaseOrEquipWeapon -> purchaseOrEquipWeapon(state, pulse.id)
        is ProfilePulse.MarkCollectionEntryViewed -> markCollectionEntryViewed(state, pulse.entry)
        is ProfilePulse.SelectCoreShape -> selectCoreShape(state, pulse.shape, commandReply = false)
        is ProfilePulse.PurchaseCharacterUpgrade -> purchaseCharacterUpgrade(state, pulse.shape)
        is ProfilePulse.PurchaseWeaponUpgrade -> purchaseWeaponUpgrade(state, pulse.id)
    }

    private fun markCollectionEntryViewed(state: ProfileState, entry: CollectionEntry): ProfileDecision {
        val collection = state.profile.collection
        val next = when (entry) {
            is CollectionEntry.Item -> collection.copy(newItemIds = (collection.newItemIds - entry.id).toImmutableSet())
            is CollectionEntry.Relic -> collection.copy(newRelicIds = (collection.newRelicIds - entry.id).toImmutableSet())
        }
        if (next == collection) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(state, state.profile.copy(collection = next))
    }

    private fun adjustPreference(
        state: ProfileState,
        adjustment: ProfilePreferenceAdjustment,
    ): ProfileDecision {
        val current = state.profile.preferences
        val next = when (adjustment) {
            is ProfilePreferenceAdjustment.SetLanguage -> current.copy(language = adjustment.language)
            ProfilePreferenceAdjustment.ToggleSoundEffects ->
                current.copy(soundEnabled = !current.soundEnabled)
            ProfilePreferenceAdjustment.ToggleMusic ->
                current.copy(musicEnabled = !current.musicEnabled)
            is ProfilePreferenceAdjustment.StepMasterVolume -> current.copy(
                masterVolume = stepPercentage(current.masterVolume, adjustment.direction, 0f, 1f),
            )
            is ProfilePreferenceAdjustment.SetMasterVolume -> current.copy(masterVolume = adjustment.percent / 100f)
            is ProfilePreferenceAdjustment.StepSimulationSpeed -> {
                val currentIndex = SIMULATION_SPEED_OPTIONS.indices.minByOrNull { index ->
                    abs(SIMULATION_SPEED_OPTIONS[index] - current.simulationSpeed)
                } ?: DEFAULT_SIMULATION_SPEED_INDEX
                current.copy(
                    simulationSpeed = SIMULATION_SPEED_OPTIONS[
                        (currentIndex + adjustment.direction.delta).coerceIn(SIMULATION_SPEED_OPTIONS.indices)
                    ],
                )
            }
            is ProfilePreferenceAdjustment.SetSimulationSpeed -> current.copy(simulationSpeed = adjustment.speed)
            is ProfilePreferenceAdjustment.StepTextScale -> current.copy(
                textScale = stepPercentage(current.textScale, adjustment.direction, 1f, 1.75f),
            )
            ProfilePreferenceAdjustment.ToggleScreenShake ->
                current.copy(screenShake = !current.screenShake)
            ProfilePreferenceAdjustment.ToggleRunStatisticsSide ->
                current.copy(runStatisticsOnLeft = !current.runStatisticsOnLeft)
            ProfilePreferenceAdjustment.ToggleMinimap ->
                current.copy(minimapEnabled = !current.minimapEnabled)
            is ProfilePreferenceAdjustment.StepParticleDensity -> current.copy(
                particleDensity = ParticleDensity.entries[
                    (current.particleDensity.ordinal + adjustment.direction.delta)
                        .coerceIn(ParticleDensity.entries.indices)
                ],
            )
            is ProfilePreferenceAdjustment.SetParticleDensity -> current.copy(particleDensity = adjustment.density)
            ProfilePreferenceAdjustment.ToggleDamageNumbers ->
                current.copy(damageNumbers = !current.damageNumbers)
            is ProfilePreferenceAdjustment.StepDamageNumberSize -> current.copy(
                damageNumberSize = DamageNumberSize.entries[
                    (current.damageNumberSize.ordinal + adjustment.direction.delta)
                        .coerceIn(DamageNumberSize.entries.indices)
                ],
            )
            is ProfilePreferenceAdjustment.SetDamageNumberSize -> current.copy(damageNumberSize = adjustment.size)
            is ProfilePreferenceAdjustment.StepDamageNumberFormat -> current.copy(
                damageNumberFormat = DamageNumberFormat.entries[
                    (current.damageNumberFormat.ordinal + adjustment.direction.delta)
                        .coerceIn(DamageNumberFormat.entries.indices)
                ],
            )
            is ProfilePreferenceAdjustment.SetDamageNumberFormat -> current.copy(damageNumberFormat = adjustment.format)
            is ProfilePreferenceAdjustment.SetColorVision -> current.copy(colorVision = adjustment.colorVision)
            is ProfilePreferenceAdjustment.StepDamageNumberTierThreshold -> {
                val currentIndex = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices.minByOrNull { index ->
                    abs(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[index] - current.damageNumberTierThreshold)
                } ?: DEFAULT_DAMAGE_THRESHOLD_INDEX
                current.copy(
                    damageNumberTierThreshold = DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS[
                        (currentIndex + adjustment.direction.delta)
                            .coerceIn(DAMAGE_NUMBER_TIER_THRESHOLD_OPTIONS.indices)
                    ],
                )
            }
            is ProfilePreferenceAdjustment.SetWindowMode -> current.copy(windowMode = adjustment.mode)
            is ProfilePreferenceAdjustment.SetWindowSize -> current.copy(windowSize = adjustment.preset)
            ProfilePreferenceAdjustment.ToggleVsync -> current.copy(vsync = !current.vsync)
            is ProfilePreferenceAdjustment.SetFrameRateCap -> current.copy(frameRateCap = adjustment.fps)
            is ProfilePreferenceAdjustment.SetRefreshRate -> current.copy(refreshRateHz = adjustment.hz)
            ProfilePreferenceAdjustment.ToggleImmersive -> current.copy(immersive = !current.immersive)
            is ProfilePreferenceAdjustment.SetInterfaceScale -> current.copy(interfaceScalePercent = adjustment.percent)
            ProfilePreferenceAdjustment.ToggleShowFps -> current.copy(showFps = !current.showFps)
            ProfilePreferenceAdjustment.TogglePauseOnFocusLoss ->
                current.copy(pauseOnFocusLoss = !current.pauseOnFocusLoss)
            is ProfilePreferenceAdjustment.SetReactionEffects -> current.copy(reactionEffects = adjustment.level)
            // A key another action holds swaps with it, so the map stays complete and conflict-free.
            is ProfilePreferenceAdjustment.SetKeyBinding ->
                current.copy(keyBindings = current.keyBindings.with(adjustment.action, adjustment.key))
            ProfilePreferenceAdjustment.ResetKeyBindings -> current.copy(keyBindings = KeyBindings.DEFAULT)
        }.normalized()
        if (next == current) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(preferences = next),
        )
    }

    private fun toggleMute(
        state: ProfileState,
    ): ProfileDecision {
        val current = state.profile.preferences
        val enable = !current.soundEnabled && !current.musicEnabled
        val next = current.copy(soundEnabled = enable, musicEnabled = enable)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(preferences = next),
            commandOutput = ProfileOutput.SettingsChanged(ProfileSettingsChanged(state.revision.next(), next)),
        )
    }

    /**
     * Buys the next rank of [id] (see [matrixNodeQuote] for the order of checks). Gates apply only
     * here, never on load.
     */
    private fun purchaseMatrixNode(
        state: ProfileState,
        id: MatrixNodeId,
    ): ProfileDecision {
        val cost = when (val quote = matrixNodeQuote(state.profile, state.policy.matrixNode(id))) {
            is PurchaseQuote.Refused -> return rejected(quote.reason)
            is PurchaseQuote.Priced -> quote.cost
        }
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = state.profile.economy.copy(matter = state.profile.economy.matter - cost),
                matrix = MatrixProgress(state.profile.matrix.ranks.raisedAt(id.ordinal)),
            ),
        )
    }

    /** Refunds the full price of every owned rank and clears the Matrix (free respec). */
    private fun resetMatrix(state: ProfileState): ProfileDecision {
        val matrix = state.profile.matrix
        if (matrix.ownedNodeCount == 0) return rejected(ProfileRejection.NoChange)
        val refund = matrixSpent(matrix, state.policy)
        val economy = state.profile.economy
        val matter = saturatedAdd(economy.matter, refund)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = PlayerEconomy(matter = matter, lifetimeMatter = max(economy.lifetimeMatter, matter)),
                matrix = MatrixProgress(),
            ),
        )
    }

    /** The Hangar's local pulse and the Session's command share one rule; only the command replies. */
    private fun selectCoreShape(
        state: ProfileState,
        shape: kinetickk.ball.content.api.CoreShape,
        commandReply: Boolean = true,
    ): ProfileDecision {
        if (!isCoreShapeUnlocked(state.profile, state.policy.coreShape(shape))) {
            return rejected(ProfileRejection.CoreShapeLocked)
        }
        if (shape == state.profile.loadout.coreShape) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                loadout = state.profile.loadout.copy(coreShape = shape),
            ),
            commandOutput = if (commandReply) {
                ProfileOutput.CoreShapeSelected(ProfileCoreShapeSelected(state.revision.next(), shape))
            } else {
                null
            },
        )
    }

    /**
     * Equips an owned weapon, or buys a locked one: the Rebirth rank must reach the weapon's gate, then the
     * Matter price is paid. Gates apply only to the purchase, never to weapons already owned.
     */
    private fun purchaseOrEquipWeapon(
        state: ProfileState,
        id: kinetickk.ball.content.api.WeaponId,
    ): ProfileDecision {
        val unlocked = state.profile.loadout.unlockedWeapons.toMutableSet()
        var economy = state.profile.economy
        if (id !in unlocked) {
            val cost = when (val quote = weaponUnlockQuote(state.profile, state.policy.weapon(id))) {
                is PurchaseQuote.Refused -> return rejected(quote.reason)
                is PurchaseQuote.Priced -> quote.cost
            }
            economy = economy.copy(matter = economy.matter - cost)
            unlocked += id
        } else if (id == state.profile.loadout.selectedWeapon) {
            return rejected(ProfileRejection.NoChange)
        }
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = economy,
                loadout = PlayerLoadout(
                    coreShape = state.profile.loadout.coreShape,
                    selectedWeapon = id,
                    unlockedWeapons = unlocked,
                ),
            ),
        )
    }

    /**
     * Buys the next Refit level of [shape] (see [characterUpgradeQuote] for the order of checks).
     * Upgrades are permanent spending, so lifetime Matter is unchanged and nothing refunds them.
     */
    private fun purchaseCharacterUpgrade(
        state: ProfileState,
        shape: kinetickk.ball.content.api.CoreShape,
    ): ProfileDecision = when (val quote = characterUpgradeQuote(state.profile, state.policy, shape)) {
        is PurchaseQuote.Refused -> rejected(quote.reason)
        is PurchaseQuote.Priced -> acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = state.profile.economy.copy(matter = state.profile.economy.matter - quote.cost),
                upgrades = state.profile.upgrades.copy(
                    characterLevels = state.profile.upgrades.characterLevels.raisedAt(shape.ordinal),
                ),
            ),
        )
    }

    /** Buys the next Honing level of [id] (see [weaponUpgradeQuote]); lifetime Matter is unchanged. */
    private fun purchaseWeaponUpgrade(
        state: ProfileState,
        id: kinetickk.ball.content.api.WeaponId,
    ): ProfileDecision = when (val quote = weaponUpgradeQuote(state.profile, state.policy, id)) {
        is PurchaseQuote.Refused -> rejected(quote.reason)
        is PurchaseQuote.Priced -> acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(
                economy = state.profile.economy.copy(matter = state.profile.economy.matter - quote.cost),
                upgrades = state.profile.upgrades.copy(
                    weaponLevels = state.profile.upgrades.weaponLevels.raisedAt(id.ordinal),
                ),
            ),
        )
    }

    /**
     * Selects [tier] for the next run. Any tier from the policy minimum up to the rank (one above the
     * highest clear) is allowed, lower tiers included, so a player is never stuck at a tier they
     * cannot clear; nothing else changes. The current tier is accepted again (it confirms the tier the
     * next run starts at), so this command always publishes its result.
     */
    private fun selectRebirthTier(
        state: ProfileState,
        tier: Int,
    ): ProfileDecision {
        val progress = state.profile.rebirthProgress
        val rebirth = state.policy.rebirth
        if (tier !in rebirth.minimumLevel..progress.rankWithin(rebirth.minimumLevel, rebirth.maximumLevel)) {
            return rejected(ProfileRejection.RebirthTierLocked)
        }
        val next = progress.copy(level = tier)
        return acceptedMutation(
            state = state,
            nextProfile = state.profile.copy(rebirthProgress = next),
            commandOutput = ProfileOutput.RebirthTierSelected(ProfileRebirthTierSelected(state.revision.next(), next)),
        )
    }

    private fun applyGameplayProgress(
        state: ProfileState,
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
    ): ProfileDecision {
        validateGameplayProgress(state, update)?.let {
            return rejected(ProfileRejection.InvalidGameplayProgress(it))
        }

        val economy = if (update.bankedMatter == 0L) {
            state.profile.economy
        } else {
            PlayerEconomy(
                matter = saturatedAdd(state.profile.economy.matter, update.bankedMatter),
                lifetimeMatter = saturatedAdd(state.profile.economy.lifetimeMatter, update.bankedMatter),
            )
        }
        val discoveries = state.profile.collection.discoveredItemIds.toMutableSet().apply {
            addAll(update.discoveredItemIds)
        }
        val rebirth = update.clearedRebirthLevel?.let { cleared ->
            // Clearing the final tier makes the victorious character Ascendant.
            val victor = update.architectDefeatedWith
            val ascended = victor?.takeIf { cleared >= ContentBounds.MAX_REBIRTH_LEVEL }
            val progress = state.profile.rebirthProgress
            progress.copy(
                highestCleared = max(progress.highestCleared, cleared),
                ascendedCharacters = if (ascended == null) progress.ascendedCharacters
                else (progress.ascendedCharacters + ascended).toImmutableSet(),
                // The victorious character keeps its highest clear; a lower tier replayed leaves it.
                characterClears = if (victor == null) progress.characterClears
                else progress.characterClears.mapIndexed { index, best ->
                    if (index == victor.ordinal) max(best, cleared) else best
                }.toImmutableList(),
            )
        } ?: state.profile.rebirthProgress
        val previousAchievements = state.profile.characterAchievements
        val achievements = CharacterAchievementProgress(
            eliteKills = saturatedAdd(previousAchievements.eliteKills, update.eliteKills.toLong()),
            dashHits = saturatedAdd(previousAchievements.dashHits, update.dashHits.toLong()),
            completedOrbits = saturatedAdd(previousAchievements.completedOrbits, update.completedOrbits.toLong()),
            architectVictories = saturatedAdd(previousAchievements.architectVictories, if (update.architectDefeatedWith != null) 1L else 0L),
            victoriousCharacters = (previousAchievements.victoriousCharacters + listOfNotNull(update.architectDefeatedWith)).toImmutableSet(),
            totalKills = saturatedAdd(previousAchievements.totalKills, update.totalKills.toLong()),
            ricochets = saturatedAdd(previousAchievements.ricochets, update.ricochets.toLong()),
            dataPickups = saturatedAdd(previousAchievements.dataPickups, update.dataPickups.toLong()),
        )
        val collection = state.profile.collection
        // A resolved Heirloom offer spends the remembered Relics before a victory in the same
        // update records the Relics bound at that victory.
        val heirlooms = update.victoryRelicIds
            ?: if (update.heirloomResolved) immutableListOf() else collection.heirloomRelicIds
        val next = state.profile.copy(
            characterAchievements = achievements,
            economy = economy,
            collection = collection.copy(
                discoveredItemIds = discoveries.toImmutableSet(),
                newItemIds = (collection.newItemIds +
                    (update.discoveredItemIds - collection.discoveredItemIds)).toImmutableSet(),
                discoveredRelicIds = (collection.discoveredRelicIds + update.discoveredRelicIds).toImmutableSet(),
                newRelicIds = (collection.newRelicIds +
                    (update.discoveredRelicIds - collection.discoveredRelicIds)).toImmutableSet(),
                discoveredReactionIds = (collection.discoveredReactionIds + update.discoveredReactionIds).toImmutableSet(),
                discoveredPairingIds = (collection.discoveredPairingIds + update.discoveredPairingIds).toImmutableSet(),
                discoveredWorldEntries = (collection.discoveredWorldEntries + update.discoveredWorldEntries).toImmutableSet(),
                heirloomRelicIds = heirlooms,
                // Oldest first; only the newest MAX_RECENT_ITEM_IDS acquisitions are remembered.
                recentItemIds = if (update.acquiredItemIds.isEmpty()) {
                    collection.recentItemIds
                } else {
                    (collection.recentItemIds + update.acquiredItemIds).takeLast(MAX_RECENT_ITEM_IDS).toImmutableList()
                },
            ),
            rebirthProgress = rebirth,
        )
        if (next == state.profile) return rejected(ProfileRejection.NoChange)
        return acceptedMutation(
            state = state,
            nextProfile = next,
            commandOutput = ProfileOutput.ProgressApplied(ProfileProgressApplied(state.revision.next())),
        )
    }

    private fun acceptedMutation(
        state: ProfileState,
        nextProfile: PlayerProfile,
        commandOutput: ProfileOutput? = null,
    ): ProfileDecision {
        check(state.persistence !is ProfilePersistenceStatus.Pending) {
            "Inline Profile cannot accept another mutation while a Resource effect is pending"
        }
        val revision = state.revision.next()
        val effectRef = resourceEffectRef(revision)
        val nextState = state.copy(
            revision = revision,
            profile = nextProfile,
            persistence = ProfilePersistenceStatus.Pending(
                effectRef = effectRef,
                snapshotRevision = revision,
            ),
        )
        val persist = ProfileOutput.PersistSnapshot(
            effectRef = effectRef,
            snapshot = ProfileSnapshot(
                revision = revision,
                profile = nextProfile,
            ),
        )
        val outputs = if (commandOutput == null) {
            immutableListOf(persist)
        } else {
            immutableListOf(persist, commandOutput)
        }
        return accepted(nextState, outputs)
    }

    private fun decideWrite(
        state: ProfileState,
        effectRef: ProfileEffectRef,
        result: ProfileWriteResult,
    ): ProfileDecision {
        val pending = checkNotNull(state.persistence as? ProfilePersistenceStatus.Pending)
        check(pending.effectRef == effectRef)
        val persistence = checkNotNull(result.toPersistenceStatus(pending))
        return accepted(state.copy(revision = state.revision.next(), persistence = persistence))
    }

    private fun mutationGate(state: ProfileState): ProfileRejection? = when (state.bootstrap) {
        ProfileBootstrapStatus.Ready -> null
        is ProfileBootstrapStatus.Blocked -> ProfileRejection.BootstrapNotReady
    }

    private fun validateGameplayProgress(
        state: ProfileState,
        update: kinetickk.ball.profile.api.GameplayProgressUpdate,
    ): ProfileGameplayProgressRejection? {
        if (update.eliteKills < 0 || update.dashHits < 0 || update.completedOrbits < 0 ||
            update.totalKills < 0 || update.ricochets < 0 || update.dataPickups < 0
        ) {
            return ProfileGameplayProgressRejection.NegativeAchievementProgress
        }
        update.architectDefeatedWith?.let { character ->
            if (!isCoreShapeUnlocked(state.profile, state.policy.coreShape(character))) {
                return ProfileGameplayProgressRejection.VictoryCharacterLocked
            }
        }
        if (update.bankedMatter < 0L) return ProfileGameplayProgressRejection.NegativeBankedMatter
        if (update.discoveredItemIds.size > state.policy.itemCount) {
            return ProfileGameplayProgressRejection.TooManyDiscoveries
        }
        update.discoveredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
            return ProfileGameplayProgressRejection.UnknownItem(it)
        }
        if (update.acquiredItemIds.size > MAX_RECENT_ITEM_IDS) {
            return ProfileGameplayProgressRejection.TooManyAcquiredItems
        }
        update.acquiredItemIds.firstOrNull { !state.policy.containsItem(it) }?.let {
            return ProfileGameplayProgressRejection.UnknownItem(it)
        }
        update.clearedRebirthLevel?.let { level ->
            if (level < state.policy.rebirth.minimumLevel) {
                return ProfileGameplayProgressRejection.ClearedLevelBelowMinimum(level)
            }
            // The run's tier was selected under the rank at its start; the live selection may have
            // moved since, so the clear is checked against the rank, never against `level`.
            if (level > state.profile.rebirthProgress.rankWithin(state.policy.rebirth.minimumLevel, state.policy.rebirth.maximumLevel)) {
                return ProfileGameplayProgressRejection.ClearedLevelAboveRank(level)
            }
        }
        update.victoryRelicIds?.let { relics ->
            if (update.clearedRebirthLevel == null) {
                return ProfileGameplayProgressRejection.VictoryRelicsWithoutClear
            }
            if (relics.size > MAX_HEIRLOOM_RELICS) return ProfileGameplayProgressRejection.TooManyVictoryRelics
            relics.firstDuplicateOrNull()?.let { return ProfileGameplayProgressRejection.DuplicateVictoryRelic(it) }
        }
        return null
    }

    /**
     * True when a new tier waits: the rank is not cleared yet and lies above the selected `level`. An
     * Ascendant profile (every tier cleared) has none, whichever tier it replays.
     */
    private fun canAdvanceRebirth(state: ProfileState): Boolean {
        val progress = state.profile.rebirthProgress
        val rank = progress.rankWithin(state.policy.rebirth.minimumLevel, state.policy.rebirth.maximumLevel)
        return state.bootstrap == ProfileBootstrapStatus.Ready && rank > progress.level && rank > progress.highestCleared
    }

    private fun ProfileWriteResult.toPersistenceStatus(
        pending: ProfilePersistenceStatus.Pending,
    ): ProfilePersistenceStatus? = when (this) {
        is ProfileWriteResult.Written -> if (revision == pending.snapshotRevision) {
            ProfilePersistenceStatus.Persisted(revision)
        } else {
            null
        }
        is ProfileWriteResult.Rejected ->
            ProfilePersistenceStatus.Rejected(pending.snapshotRevision, reason)
        is ProfileWriteResult.ResourceFailure ->
            ProfilePersistenceStatus.ResourceFailure(pending.snapshotRevision, reason)
        is ProfileWriteResult.OutcomeUnknown ->
            ProfilePersistenceStatus.OutcomeUnknown(pending.snapshotRevision, reason)
    }

    private fun resourceEffectRef(revision: ProfileRevision): ProfileEffectRef =
        ProfileEffectRef(sourceRevision = revision, ordinal = PROFILE_RESOURCE_OUTPUT_ORDINAL)

    private fun ProfileRevision.next(): ProfileRevision {
        check(value < Long.MAX_VALUE) { "Profile revision exhausted" }
        return ProfileRevision(value + 1L)
    }

    private fun PlayerProfile.toGameplaySnapshot(): GameplayProfileSnapshot = GameplayProfileSnapshot(
        preferences = preferences,
        economy = economy,
        loadout = loadout,
        matrix = matrix,
        collection = collection,
        rebirthProgress = rebirthProgress,
        upgrades = upgrades,
    )

    private fun accepted(
        nextState: ProfileState,
        outputs: ImmutableList<ProfileOutput> = immutableListOf(),
    ): ProfileDecision.Accepted {
        check(outputs.size <= MAX_PROFILE_OUTPUTS_PER_DECISION) {
            "Profile semantic output limit exceeded"
        }
        return ProfileDecision.Accepted(ProfileAcceptedFrame(nextState, outputs))
    }

    private fun rejected(reason: ProfileRejection): ProfileDecision.Rejected =
        ProfileDecision.Rejected(reason)

}

private const val PROFILE_RESOURCE_OUTPUT_ORDINAL: Int = 0

private val PreferenceAdjustmentDirection.delta: Int
    get() = when (this) {
        PreferenceAdjustmentDirection.DECREASE -> -1
        PreferenceAdjustmentDirection.INCREASE -> 1
    }

private fun stepPercentage(
    value: Float,
    direction: PreferenceAdjustmentDirection,
    minimum: Float,
    maximum: Float,
): Float {
    val nextPercent = (value * 100f).roundToInt() + direction.delta
    return (nextPercent / 100f).coerceIn(minimum, maximum)
}

private fun <T> List<T>.firstDuplicateOrNull(): T? {
    val seen = HashSet<T>(size)
    return firstOrNull { !seen.add(it) }
}

internal fun saturatedAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private const val DEFAULT_SIMULATION_SPEED_INDEX: Int = 2
private const val DEFAULT_DAMAGE_THRESHOLD_INDEX: Int = 2

/** Matter paid for every owned Matrix rank under [policy] (saturating). */
internal fun matrixSpent(matrix: MatrixProgress, policy: kinetickk.ball.content.api.ProfilePolicySnapshot): Long =
    policy.matrixNodes.fold(0L) { total, definition -> saturatedAdd(total, definition.spent(matrix.rank(definition.id))) }
