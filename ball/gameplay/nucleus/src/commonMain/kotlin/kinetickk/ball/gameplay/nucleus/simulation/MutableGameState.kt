// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery

import kinetickk.foundation.random.CloneableXorWowRandom
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MAX_RECENT_ITEM_IDS
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.rank
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
    runMode: RunMode = RunMode.STANDARD,
    offerRunOmen: Boolean = false,
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
        const val MAX_ECHO_HITS = 256
        const val MAX_BUFFS = 16
        const val MAX_CHAIN_SCRATCH = 21
        const val REACTION_BURST = 12f
        const val BREAK_BURST = 16f
        const val STASIS_SECONDS = 3f
        const val REWARD_SEED_SALT = 0x5EED_A11
        /** The draft deck's own stream (seed xor salt): dealing never draws from [rewardRandom]. */
        const val DECK_SEED_SALT = 0x0DEC_C0DE
        const val SPAWN_SEED_SALT = 0x5A_A4_D1_E7
        /** The site stream's salt (seed xor salt): window kinds never shift offers, spawns or crits. */
        const val SITE_SEED_SALT = 0x517E_5EED
        const val MAX_SEVERED_RELICS = 4
        const val MAX_GAMEPLAY_SOUND_CUES = 32
        const val MAX_WEAPON_NODES = 12
        const val MAX_WEAPON_ORBITALS = 8
        const val MAX_CHOICES = 4
        /** Volatile Remains bursts waiting at once (Rebirth R4); more deaths in the window add none. */
        const val MAX_VOLATILE_BURSTS = 48
        const val MAX_TRAIL_SAMPLES_PER_UPDATE = 32
        const val MAX_GUARD_SHARDS = 6
        const val SKILL_INPUT_BUFFER_SECONDS = 0.16f
        /** Enemies one skill cast may pick at once (Catalyze's largest target cap). */
        const val MAX_SKILL_SCRATCH = 10
    }

    internal var gameplayRandom: CloneableXorWowRandom = reductionSource?.gameplayRandom?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    } ?: CloneableXorWowRandom(seed)
    /** Offers draw from their own stream, so drafting choices never shift spawns or crits. */
    internal var rewardRandom: CloneableXorWowRandom = reductionSource?.rewardRandom?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    } ?: CloneableXorWowRandom(seed xor REWARD_SEED_SALT)
    /**
     * Every spawn, spawn position, enemy type, Elite and Herald aspect and director draw comes from this
     * stream, so spawning never shifts crits and procs, and a different run seed reshapes the waves.
     */
    internal var spawnRandom: CloneableXorWowRandom = reductionSource?.spawnRandom?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    } ?: CloneableXorWowRandom(seed xor SPAWN_SEED_SALT)
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
    /** Owned Core Matrix ranks by content node index, captured from the profile at bootstrap. */
    internal val matrixRanks: CopyOnWriteIntArray = reductionSource?.matrixRanks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.matrixNodes.size) { index ->
            val definition = content.matrixNodes[index]
            (bootstrapProgress?.matrix?.rank(definition.id) ?: 0).coerceIn(0, definition.maxRanks)
        })
    /** Folded Matrix effects of [matrixRanks]; immutable, refolded when a run starts. */
    internal var matrix: MatrixModifiers = reductionSource?.matrix ?: foldMatrix()
    /** Bought Refit levels by [CoreShape.ordinal], captured from the profile at bootstrap; a run never writes them. */
    internal val characterUpgradeLevels: CopyOnWriteIntArray = reductionSource?.characterUpgradeLevels?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(CoreShape.entries.size) { index ->
            (bootstrapProgress?.upgrades?.characterLevels?.getOrElse(index) { 0 } ?: 0)
                .coerceIn(0, content.upgrades.maxLevel)
        })
    /** Bought Honing levels by [WeaponId.ordinal], captured from the profile at bootstrap; a run never writes them. */
    internal val weaponUpgradeLevels: CopyOnWriteIntArray = reductionSource?.weaponUpgradeLevels?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(WeaponId.entries.size) { index ->
            (bootstrapProgress?.upgrades?.weaponLevels?.getOrElse(index) { 0 } ?: 0)
                .coerceIn(0, content.upgrades.maxLevel)
        })
    /** The selected character's Refit level, fixed when a run starts. */
    internal var characterUpgradeLevel: Int = reductionSource?.characterUpgradeLevel ?: 0
    /** Refit's character-mechanic damage multiplier, fixed when a run starts. */
    internal var characterUpgradePower: Float = reductionSource?.characterUpgradePower ?: 1f
    internal val discoveredItemIds: CopyOnWriteMutableSet<Int> = reductionSource?.discoveredItemIds?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteMutableSet(
            bootstrapProgress?.collection?.discoveredItemIds
                ?.filterTo(mutableSetOf()) { content.item(it) != null }
                ?: mutableSetOf(),
        )
    internal var discoveredRelicMask: Long = reductionSource?.discoveredRelicMask
        ?: bootstrapProgress?.collection?.discoveredRelicIds?.fold(0L) { mask, id -> mask or (1L shl id.ordinal) } ?: 0L
    internal var pendingDiscoveredRelicMask: Long = reductionSource?.pendingDiscoveredRelicMask ?: 0L
    internal var pendingDiscoveredItemIdStorage: CopyOnWriteMutableSet<Int>? =
        reductionSource?.pendingDiscoveredItemIdStorage?.fork()
    internal val pendingDiscoveredItemIds: PendingDiscoveredItemIdBuffer
        get() = PendingDiscoveredItemIdBuffer(this)
    internal val itemStacks: CopyOnWriteIntArray = reductionSource?.itemStacks?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.items.size))
    internal val rebornItems: CopyOnWriteIntArray = reductionSource?.rebornItems?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.items.size))
    /**
     * Build gains in order: item ids at first acquisition (a replaced active item's id leaves it) and relic binds or
     * rank-three upgrades as [relicGain] entries. Decides affinity ties. Immutable value, replaced on change.
     */
    internal var acquisitionOrder: List<Int> = reductionSource?.acquisitionOrder ?: emptyList()
    /**
     * First acquisitions not yet reported to the Profile's recent-item history, oldest first; immutable and replaced on
     * change, at most [MAX_RECENT_ITEM_IDS] (the oldest drop first).
     */
    internal var pendingAcquiredItemIds: List<Int> = reductionSource?.pendingAcquiredItemIds ?: emptyList()
    /** The draft deck stream's seed ([DECK_SEED_SALT]); the deck is dealt from it when the run starts. */
    internal val deckSeed: Int = reductionSource?.deckSeed ?: (seed xor DECK_SEED_SALT)
    // ---- 0.6.0 EASY ----
    /** Baby mode (RunMode.EASY), fixed when the run is created; a run never changes it. */
    internal val easyMode: Boolean = reductionSource?.easyMode ?: (runMode == RunMode.EASY)
    // ---- 0.6.0 VARIETY ----
    /** The production start path offers a run omen beside the Heirloom; fixed when the run is created. */
    internal val offersRunOmen: Boolean = reductionSource?.offersRunOmen ?: offerRunOmen
    /** The run's draft deck (immutable, replaced when a refill deals); every item until the run deals one. */
    internal var itemDeck: ItemDeck = reductionSource?.itemDeck ?: ItemDeck.EVERYTHING
    /** Down-weights from the Profile's recent acquisitions, fixed for the run. */
    internal val itemHistory: ItemHistoryWeights = reductionSource?.itemHistory ?: ItemHistoryWeights.of(
        bootstrapProgress?.recentItemIds.orEmpty(), content.items.size, MAX_RECENT_ITEM_IDS,
        content.build.policy.draftDeck.historyPenalty, content.build.policy.draftDeck.historyFloor,
    )
    /** Immutable compiled build shared by reduction siblings; replaced by [recompileBuild]. */
    internal var build: CompiledBuild = reductionSource?.build ?: CompiledBuild.empty(content)
    internal val clauseReadyAt: CopyOnWriteFloatArray = reductionSource?.clauseReadyAt?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CopyOnWriteFloatArray(FloatArray(content.totalClauseCount))
    internal val clauseCounters: CopyOnWriteIntArray = reductionSource?.clauseCounters?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CopyOnWriteIntArray(IntArray(content.totalClauseCount))
    /** Fractional status stacks per item, so stack magnitudes apply deterministically without RNG. */
    internal val statusAccumulators: CopyOnWriteFloatArray = reductionSource?.statusAccumulators?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CopyOnWriteFloatArray(FloatArray(content.items.size))
    /** Earliest time each item may apply statuses again (per-item rate bound). */
    internal val itemStatusReadyAt: CopyOnWriteFloatArray = reductionSource?.itemStatusReadyAt?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CopyOnWriteFloatArray(FloatArray(content.items.size))
    internal var buffs: List<ActiveBuff> = reductionSource?.buffs ?: emptyList()
    /** Lingering fields; a full reduction forks the table, a scalar-input reduction shares it. */
    internal val combatFieldTable: CombatFieldTable = reductionSource?.combatFieldTable?.let {
        if (shareStableReductionStorage) it else it.fork()
    } ?: CombatFieldTable()
    /** Field kinds the Core stood in at the last field pass, one bit per [CombatFieldKind] ordinal. */
    internal var coreFieldMask: Int = reductionSource?.coreFieldMask ?: 0
    /** When entering a field may next count as a field entry. */
    internal var fieldEnterReadyAt: Float = reductionSource?.fieldEnterReadyAt ?: 0f
    // Core reaction state: lingering effects reactions leave on the Core (absolute run times).
    internal var ablazeUntil: Float = reductionSource?.ablazeUntil ?: 0f
    internal var frictionlessUntil: Float = reductionSource?.frictionlessUntil ?: 0f
    internal var blastJumpUntil: Float = reductionSource?.blastJumpUntil ?: 0f
    internal var weaveUntil: Float = reductionSource?.weaveUntil ?: 0f
    internal var weaveDamage: Float = reductionSource?.weaveDamage ?: 0f
    internal var weaveRange: Float = reductionSource?.weaveRange ?: 0f
    /** Ice shards orbiting the Core (at most [MAX_GUARD_SHARDS]), until when and what each strikes for. */
    internal var guardShards: Int = reductionSource?.guardShards ?: 0
    internal var guardUntil: Float = reductionSource?.guardUntil ?: 0f
    internal var guardDamage: Float = reductionSource?.guardDamage ?: 0f
    /** The one Void Anchor (enemy id, -1 for none) and when it lapses. */
    internal var voidAnchorId: Int = reductionSource?.voidAnchorId ?: -1
    internal var voidAnchorUntil: Float = reductionSource?.voidAnchorUntil ?: 0f
    internal var overcurrentReadyAt: Float = reductionSource?.overcurrentReadyAt ?: 0f
    internal var flameRefundReadyAt: Float = reductionSource?.flameRefundReadyAt ?: 0f
    /** The enemy whose execute is under way, so its Data latches to the Core; -1 between executes. */
    internal var erasureLatchId: Int = reductionSource?.erasureLatchId ?: -1
    /** When a reaction or item may next push the Core. */
    internal var coreImpulseReadyAt: Float = reductionSource?.coreImpulseReadyAt ?: 0f
    internal var primedDashUntil: Float = reductionSource?.primedDashUntil ?: 0f
    /** Dash heat spent toward the next Dash-heat-to-skills refund (below its step of 10). */
    internal var dashHeatBank: Float = reductionSource?.dashHeatBank ?: 0f
    internal var phaseUntil: Float = reductionSource?.phaseUntil ?: 0f
    /** Seismic's keystone keeps two pending aftershocks: when each strikes (negative when free), where and how hard. */
    internal var aftershockAt0: Float = reductionSource?.aftershockAt0 ?: -1f
    internal var aftershockX0: Float = reductionSource?.aftershockX0 ?: 0f
    internal var aftershockY0: Float = reductionSource?.aftershockY0 ?: 0f
    internal var aftershockDamage0: Float = reductionSource?.aftershockDamage0 ?: 0f
    internal var aftershockAt1: Float = reductionSource?.aftershockAt1 ?: -1f
    internal var aftershockX1: Float = reductionSource?.aftershockX1 ?: 0f
    internal var aftershockY1: Float = reductionSource?.aftershockY1 ?: 0f
    internal var aftershockDamage1: Float = reductionSource?.aftershockDamage1 ?: 0f
    /** Erased bolts and cashed marks announced within the current fixed step; zero between steps. */
    internal var shotErasedDispatches: Int = reductionSource?.shotErasedDispatches ?: 0
    internal var markDispatches: Int = reductionSource?.markDispatches ?: 0
    /** Hostile bolts erased this run (a run statistic). */
    internal var shotsErased: Long = reductionSource?.shotsErased ?: 0L
    // Skill and Apex input: buffered slot requests and their age, the buffered Apex request.
    internal var skillBufferMask: Int = reductionSource?.skillBufferMask ?: 0
    internal var skillBufferTime: Float = reductionSource?.skillBufferTime ?: 0f
    internal var apexBufferTime: Float = reductionSource?.apexBufferTime ?: 0f
    /** What the skill slots hold: the owned active items' effects; immutable, replaced when an active item binds, ranks or is replaced. */
    internal var skillLoadout: SkillLoadout = reductionSource?.skillLoadout ?: SkillLoadout.EMPTY
    /** Skill and Apex timing; a full reduction forks it, a scalar-input reduction shares it. */
    internal var skillRuntime: SkillRuntime = reductionSource?.skillRuntime?.let { source ->
        if (shareStableReductionStorage) source else source.fork()
    } ?: SkillRuntime()
    /** Candidate-private scratch of one skill cast (picked targets); never read across casts. */
    internal val skillScratch: IntArray = IntArray(MAX_SKILL_SCRATCH)
    /** The slot casting right now, -1 between casts; the cast's damaged-enemy count and the bosses it struck. */
    internal var castSlot: Int = -1
    internal var castHits: Int = 0
    /** Enemies the running element-less cast attuned (SkillAttunement). */
    internal var castAttunedStacks: Int = 0
    internal var castPrimaryId: Int = -1
    internal var castBossHitFirst: Int = -1
    internal var castBossHitSecond: Int = -1
    internal var reactionTokens: Float = reductionSource?.reactionTokens ?: REACTION_BURST
    internal var breakTokens: Float = reductionSource?.breakTokens ?: BREAK_BURST
    internal var discoveredReactionMask: Int = reductionSource?.discoveredReactionMask
        ?: bootstrapProgress?.collection?.discoveredReactionIds?.fold(0) { mask, id -> mask or (1 shl id.ordinal) } ?: 0
    internal var pendingDiscoveredReactionMask: Int = reductionSource?.pendingDiscoveredReactionMask ?: 0
    internal var discoveredPairingMask: Int = reductionSource?.discoveredPairingMask
        ?: bootstrapProgress?.collection?.discoveredPairingIds?.fold(0) { mask, id -> mask or (1 shl id.ordinal) } ?: 0
    internal var pendingDiscoveredPairingMask: Int = reductionSource?.pendingDiscoveredPairingMask ?: 0
    internal var heirloomCandidates: List<RelicId> = reductionSource?.heirloomCandidates
        ?: bootstrapProgress?.collection?.heirloomRelicIds?.toList().orEmpty()
    internal var heirloomPending: Boolean = reductionSource?.heirloomPending ?: false
    internal var heirloomOfferActive: Boolean = reductionSource?.heirloomOfferActive ?: false
    internal var pendingHeirloomResolved: Boolean = reductionSource?.pendingHeirloomResolved ?: false
    internal var pendingVictoryRelics: List<RelicId>? = reductionSource?.pendingVictoryRelics
    internal var severedRelics: List<SeveredRelic> = reductionSource?.severedRelics ?: emptyList()
    internal var ouroborosSpent: Boolean = reductionSource?.ouroborosSpent ?: false
    internal var ouroborosLockedUntil: Float = reductionSource?.ouroborosLockedUntil ?: 0f
    internal var invulnerableUntil: Float = reductionSource?.invulnerableUntil ?: 0f
    internal var glassInvulnerabilityReadyAt: Float = reductionSource?.glassInvulnerabilityReadyAt ?: 0f
    internal var bulwarkInvulnerabilityReadyAt: Float = reductionSource?.bulwarkInvulnerabilityReadyAt ?: 0f
    internal var stormCharge: Float = reductionSource?.stormCharge ?: 0f
    internal var stasisMeter: Float = reductionSource?.stasisMeter ?: STASIS_SECONDS
    internal var stasisBank: Int = reductionSource?.stasisBank ?: 0
    internal var stasisLockedUntil: Float = reductionSource?.stasisLockedUntil ?: 0f
    internal var sinceTurn: Float = reductionSource?.sinceTurn ?: 0f
    internal var gyreReadyAt: Float = reductionSource?.gyreReadyAt ?: 0f
    /** Endless Gyre's delayed second ring: when it fires (negative when none is pending) and its damage. */
    internal var gyreEchoAt: Float = reductionSource?.gyreEchoAt ?: -1f
    internal var gyreEchoDamage: Float = reductionSource?.gyreEchoDamage ?: 0f
    internal var overdriveBurnoutUntil: Float = reductionSource?.overdriveBurnoutUntil ?: 0f
    internal var overdriveExtension: Float = reductionSource?.overdriveExtension ?: 0f
    internal var overdriveKills: Int = reductionSource?.overdriveKills ?: 0
    internal var aegisAbsorbed: Float = reductionSource?.aegisAbsorbed ?: 0f
    internal var aegisNovaReadyAt: Float = reductionSource?.aegisNovaReadyAt ?: 0f
    internal var shieldBreakArmed: Boolean = reductionSource?.shieldBreakArmed ?: false
    internal var shieldBreakReadyAt: Float = reductionSource?.shieldBreakReadyAt ?: 0f
    internal var overheatArmed: Boolean = reductionSource?.overheatArmed ?: true
    internal var wasBraking: Boolean = reductionSource?.wasBraking ?: false
    internal var brakeHoldTime: Float = reductionSource?.brakeHoldTime ?: 0f
    internal var brakeShedSpeed: Float = reductionSource?.brakeShedSpeed ?: 0f
    internal var critMissStreak: Int = reductionSource?.critMissStreak ?: 0
    internal var freeDashPrimed: Boolean = reductionSource?.freeDashPrimed ?: false
    internal var freeDashReadyAt: Float = reductionSource?.freeDashReadyAt ?: 0f
    internal var comboKillCounter: Int = reductionSource?.comboKillCounter ?: 0
    internal var glassCharge: Float = reductionSource?.glassCharge ?: 0f
    /** Maximum integrity Null Covenant's liability currently withholds (0 when none). */
    internal var integrityLiabilityCut: Float = reductionSource?.integrityLiabilityCut ?: 0f
    internal var cinderRecoilReadyAt: Float = reductionSource?.cinderRecoilReadyAt ?: 0f
    internal var offerPity: Int = reductionSource?.offerPity ?: 0
    /** Item offers opened this run; the Matrix widens every n-th one. */
    internal var itemOffersOpened: Int = reductionSource?.itemOffersOpened ?: 0
    internal var secondWindSpent: Boolean = reductionSource?.secondWindSpent ?: false
    /** When the next heat-free Matrix Dash is ready. */
    internal var matrixFreeDashReadyAt: Float = reductionSource?.matrixFreeDashReadyAt ?: 0f
    internal var dashHitThisDash: Boolean = reductionSource?.dashHitThisDash ?: false
    internal var clauseDepth: Int = reductionSource?.clauseDepth ?: 0
    internal var arsenalCounter: Int = reductionSource?.arsenalCounter ?: 0
    internal var necroticClock: Float = reductionSource?.necroticClock ?: 0f
    /** Visual cue budget for statuses and reactions within the current fixed step. */
    internal var statusCuesThisStep: Int = reductionSource?.statusCuesThisStep ?: 0
    /** Candidate-private scratch for bounded chain targeting; never read across steps. */
    internal val chainScratch: IntArray = IntArray(MAX_CHAIN_SCRATCH)
    /** Nesting level of [chainScratch] use within the current step; zero between steps. */
    internal var chainDepth: Int = 0
    /** The enemy whose Freeze the current hit is shattering, so that hit's clauses still see it Frozen; -1 between hits. */
    internal var shatteringEnemyId: Int = -1
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
    /** Mutable character state; a full reduction forks it (scalars copied, buffers copy-on-write). */
    internal var characterRuntime: CharacterRuntime = reductionSource?.characterRuntime?.let { source ->
        if (shareStableReductionStorage) source else source.fork()
    } ?: CharacterRuntime()
    /**
     * Owned state of the equipped weapon (entities, proc gates, element cadences, scaling inputs); a full
     * reduction forks it, a scalar-input reduction shares it.
     */
    internal var weaponRuntime: WeaponRuntime = reductionSource?.weaponRuntime?.let { source ->
        if (shareStableReductionStorage) source else source.fork()
    } ?: WeaponRuntime()
    internal var pendingEliteKills: Int = reductionSource?.pendingEliteKills ?: 0
    internal var pendingDashHits: Int = reductionSource?.pendingDashHits ?: 0
    internal var pendingCompletedOrbits: Int = reductionSource?.pendingCompletedOrbits ?: 0
    internal var pendingArchitectDefeatedWith: CoreShape? = reductionSource?.pendingArchitectDefeatedWith
    /**
     * Cumulative achievement counters awaiting publication. They ride along with any other progress
     * update and are otherwise published once the run banks (exit, death, victory), so a kill or a
     * pickup never forces a Profile write on its own.
     */
    internal var pendingTotalKills: Int = reductionSource?.pendingTotalKills ?: 0
    /** Hard obstacle bounces awaiting publication; the obstacle system counts them. */
    internal var pendingRicochets: Int = reductionSource?.pendingRicochets ?: 0
    internal var pendingDataPickups: Int = reductionSource?.pendingDataPickups ?: 0
    /** The run banked, so the next progress update also publishes the deferred counters. */
    internal var achievementFlushDue: Boolean = reductionSource?.achievementFlushDue ?: false
    // ---- 0.6.0 SITES ----
    /** Window sites: the two offers of a window, or the one active trial. Immutable, replaced on change. */
    internal var sites: List<SiteState> = reductionSource?.sites ?: emptyList()
    internal var nextSiteWindowIndex: Int = reductionSource?.nextSiteWindowIndex ?: 0
    internal var pendingDirectedRewards: List<kinetickk.ball.content.api.DirectedReward> = reductionSource?.pendingDirectedRewards ?: emptyList()
    internal var directedReward: kinetickk.ball.content.api.DirectedReward? = reductionSource?.directedReward
    internal var selectedRewardFocus: kinetickk.ball.content.api.RewardFocus? = reductionSource?.selectedRewardFocus
    /** Window kinds draw from their own stream, so sites never shift offers, spawns or crits. */
    internal var siteRandom: CloneableXorWowRandom = reductionSource?.siteRandom?.let { source ->
        if (shareStableReductionStorage) source else source.copy()
    } ?: CloneableXorWowRandom(seed xor SITE_SEED_SALT)
    internal var ambientSites: AmbientSiteWindow = reductionSource?.ambientSites ?: AmbientSiteWindow.EMPTY
    internal var secretSigil: SiteState? = reductionSource?.secretSigil
    /** Ambient sites opened this run, oldest first, at most [ContentBounds.MAX_OPENED_SITES]. */
    internal var openedSiteIds: List<Long> = reductionSource?.openedSiteIds ?: emptyList()
    /** [RunSecretFlag] bits of this run. */
    internal var runSecretFlags: Int = reductionSource?.runSecretFlags ?: 0
    /** World discoveries known at start plus this run's; the pending mask holds the ones not yet published. */
    internal var worldDiscoveryMask: Int = reductionSource?.worldDiscoveryMask
        ?: bootstrapProgress?.collection?.discoveredWorldEntries?.fold(0) { mask, id -> mask or id.bit } ?: 0
    internal var pendingWorldDiscoveryMask: Int = reductionSource?.pendingWorldDiscoveryMask ?: 0
    /** Rarity-floor item offers waiting, at most the site policy's bound. */
    internal var pendingFloorOffers: List<ItemRarity> = reductionSource?.pendingFloorOffers ?: emptyList()
    /** The Golden Drifter alive (−1: none) and the run second it escapes. */
    internal var goldenDrifterId: Int = reductionSource?.goldenDrifterId ?: -1
    internal var goldenDrifterUntil: Float = reductionSource?.goldenDrifterUntil ?: 0f
    /** Spin Cycle: completed one-second buckets of signed rotation, the running bucket, its clock and last angle. */
    internal var spinBuckets: List<Float> = reductionSource?.spinBuckets ?: emptyList()
    internal var spinCurrent: Float = reductionSource?.spinCurrent ?: 0f
    internal var spinClock: Float = reductionSource?.spinClock ?: 0f
    internal var spinLastAngle: Float = reductionSource?.spinLastAngle ?: 0f
    internal var spinPrimed: Boolean = reductionSource?.spinPrimed ?: false
    // ---- 0.6.0 VARIETY ----
    internal var omenPending: Boolean = reductionSource?.omenPending ?: false
    internal var runOmen: OmenId? = reductionSource?.runOmen
    /** Rebirth mutators a run modifier added (bit per ordinal), on top of the tier's own. */
    internal var runMutatorMask: Int = reductionSource?.runMutatorMask ?: 0
    internal var directorPersonality: DirectorPersonalityId? = reductionSource?.directorPersonality
    internal var featuredAspect: Int = reductionSource?.featuredAspect ?: -1
    internal var surgeIndex: Int = reductionSource?.surgeIndex ?: 0
    internal var surgeThemeType: Int = reductionSource?.surgeThemeType ?: -1
    internal var varietyAnnouncement: Int = reductionSource?.varietyAnnouncement ?: 0
    internal var keynoteTag: BuildTag? = reductionSource?.keynoteTag
    internal var keynoteElement: StatusElement? = reductionSource?.keynoteElement
    /** At most [ContentBounds.MAX_WORLD_EVENTS_PER_RUN] plans. */
    internal var worldEventPlans: ImmutableList<WorldEventPlan> = reductionSource?.worldEventPlans ?: immutableListOf()
    internal var nextWorldEvent: Int = reductionSource?.nextWorldEvent ?: 0
    internal var worldEvent: WorldEventProjection? = reductionSource?.worldEvent
    internal var worldEventPulse: Int = reductionSource?.worldEventPulse ?: 0
    internal var worldEventNextPulseAt: Float = reductionSource?.worldEventNextPulseAt ?: 0f
    internal var eventSignals: ImmutableList<EventSignalProjection> = reductionSource?.eventSignals ?: immutableListOf()
    internal var nextSignalSlot: Int = reductionSource?.nextSignalSlot ?: 0
    internal var signalsSpawned: Int = reductionSource?.signalsSpawned ?: 0
    internal var signalsEngaged: Int = reductionSource?.signalsEngaged ?: 0
    /** At most [ContentBounds.MAX_PENDING_SIGNAL_PROMPTS] prompts. */
    internal var pendingSignalPrompts: ImmutableList<SignalPrompt> = reductionSource?.pendingSignalPrompts ?: immutableListOf()
    /** The open item offer's rarity floor (null: none); rerolls keep it, finishing the choice clears it. */
    internal var offerRarityFloor: ItemRarity? = reductionSource?.offerRarityFloor

    internal var nextEntityId: Int = reductionSource?.nextEntityId ?: 1
    internal var spawnClock: Float = reductionSource?.spawnClock ?: 0f
    internal var nextEliteAt: Float = reductionSource?.nextEliteAt ?: content.tempo.firstEliteAtSeconds
    /** Elites spawned this run. */
    internal var elitesSpawned: Int = reductionSource?.elitesSpawned ?: 0
    /** Heralds spawned this run; each scheduled Herald arrives once. */
    internal var heraldsSpawned: Int = reductionSource?.heraldsSpawned ?: 0
    /** Spawn director pacing phase ([SpawnDirectorPhase] ordinal) and the run time it ends. */
    internal var directorPhase: Int = reductionSource?.directorPhase ?: 0
    internal var directorPhaseUntil: Float = reductionSource?.directorPhaseUntil ?: 0f
    /** Enemies' worth of spawn budget banked by the director; events spend it. */
    internal var spawnBudget: Float = reductionSource?.spawnBudget ?: 0f
    /** The planned next event ([SpawnEventKind] ordinal, or -1), its size and its single enemy type (-1 when mixed). */
    internal var pendingEventKind: Int = reductionSource?.pendingEventKind ?: -1
    internal var pendingEventSize: Int = reductionSource?.pendingEventSize ?: 0
    internal var pendingEventType: Int = reductionSource?.pendingEventType ?: -1
    /** Enemy types (bit per ordinal) used by the last and the previous event; they spawn at half weight. */
    internal var recentEventTypes: Long = reductionSource?.recentEventTypes ?: 0L
    internal var previousEventTypes: Long = reductionSource?.previousEventTypes ?: 0L
    // ---- 0.6.0 ENEMIES ----
    /** Secret boss kinds spawned this run (bit per EnemyType ordinal); each arrives at most once. */
    internal var secretBossMask: Long = reductionSource?.secretBossMask ?: 0L
    /** Live encounter groups, at most MAX_ACTIVE_ENCOUNTERS; value-only, replaced on change. */
    internal var encounters: ImmutableList<EncounterState> = reductionSource?.encounters ?: immutableListOf()
    internal var ambushReadyAt: Float = reductionSource?.ambushReadyAt ?: 0f
    internal var encircleReadyAt: Float = reductionSource?.encircleReadyAt ?: 0f
    internal var wallReadyAt: Float = reductionSource?.wallReadyAt ?: 0f
    /** When an event may next exceed the ordinary cap by its own size. */
    internal var capOverflowReadyAt: Float = reductionSource?.capOverflowReadyAt ?: 0f
    /** Seconds the Core has kept ambush speed without a break. */
    internal var ambushSpeedSeconds: Float = reductionSource?.ambushSpeedSeconds ?: 0f
    /** Events fired this run and the kind, planned size and spawned count of the last one. */
    internal var spawnEventCount: Int = reductionSource?.spawnEventCount ?: 0
    internal var lastSpawnEventKind: Int = reductionSource?.lastSpawnEventKind ?: -1
    internal var lastSpawnEventPlanned: Int = reductionSource?.lastSpawnEventPlanned ?: 0
    internal var lastSpawnEventSpawned: Int = reductionSource?.lastSpawnEventSpawned ?: 0
    /** Seed of this run; obstacle chunks hash it with their absolute chunk. */
    internal val runSeed: Int = reductionSource?.runSeed ?: seed
    /** Accumulated rebase shift: an absolute world position is the run-local position plus this origin. */
    internal var worldOriginX: Double = reductionSource?.worldOriginX ?: 0.0
    internal var worldOriginY: Double = reductionSource?.worldOriginY ?: 0.0
    /** Generated obstacle window around the Core; an immutable value replaced on change. */
    internal var obstacleField: ObstacleField = reductionSource?.obstacleField ?: ObstacleField.EMPTY
    /** Destroyed unstable asteroids, oldest first, at most [ContentBounds.MAX_DESTROYED_OBSTACLES]. */
    internal var destroyedObstacleIds: List<Long> = reductionSource?.destroyedObstacleIds ?: emptyList()
    // ---- 0.6.0 DIMENSIONS ----
    /** The run's world layer; only a layer switch writes it. */
    internal var worldLayer: WorldLayer = reductionSource?.worldLayer ?: WorldLayer.BASE
    /** The active keep-out of the current layer's generation (null: the origin safe radius applies). */
    internal var worldKeepOut: WorldKeepOut? = reductionSource?.worldKeepOut
    /** Run boons owned, in grant order, at most [ContentBounds.MAX_RUN_BOONS]. */
    internal var runBoons: ImmutableList<Int> = reductionSource?.runBoons ?: immutableListOf()
    /** Rift offers, visited and completed layers, the open glitch rift, the visit and its pending rewards; a value. */
    internal var dimensionRun: DimensionRunState = reductionSource?.dimensionRun ?: DimensionRunState.EMPTY
    /** Seconds the Core has flown below the layer's pace without a break (Nadir heat). */
    internal var dimensionSlowSeconds: Float = reductionSource?.dimensionSlowSeconds ?: 0f
    /** Seconds toward the next scorch tick once the heat grace ran out. */
    internal var dimensionScorchClock: Float = reductionSource?.dimensionScorchClock ?: 0f
    /** Visit time on the layer's own clock (the Archive's runs with the Core's speed). */
    internal var dimensionLocalClock: Float = reductionSource?.dimensionLocalClock ?: 0f
    /** The layer whose reward the open choice is (null: the open choice is no dimension reward). */
    internal var dimensionRewardLayer: WorldLayer? = reductionSource?.dimensionRewardLayer
    /** Run time before which an asteroid collision deals no further damage and fires no ricochet. */
    internal var obstacleHitReadyAt: Float = reductionSource?.obstacleHitReadyAt ?: 0f
    // ---- 0.6.0 TERRAIN-P ----
    /** The solid surface the last hurting collision struck ([NO_SURFACE_ID]: none). */
    internal var obstacleHitSurfaceId: Long = reductionSource?.obstacleHitSurfaceId ?: NO_SURFACE_ID
    /** Run time before which Near Miss grants no further Overdrive. */
    internal var nearMissReadyAt: Float = reductionSource?.nearMissReadyAt ?: 0f
    // ---- 0.6.0 TERRAIN-K ----
    /** Gap from the Core's surface to the nearest solid obstacle surface within reach; +∞ when none (NearObstacle). */
    internal var coreObstacleGap: Float = reductionSource?.coreObstacleGap ?: Float.POSITIVE_INFINITY
    /** Run time before which GRAZE cannot fire again. */
    internal var grazeReadyAt: Float = reductionSource?.grazeReadyAt ?: 0f
    /** The gravity knot whose influence holds the Core ([NO_OBSTACLE_ID] outside) and the heading it entered with. */
    internal var slingWellId: Long = reductionSource?.slingWellId ?: NO_OBSTACLE_ID
    internal var slingEntryHeading: Float = reductionSource?.slingEntryHeading ?: 0f
    /**
     * End of the invulnerability a revive (Second Wind, Ouroboros) granted: the only window a lethal
     * collision honours; other invulnerability (Glass Comet, Bulwark) does not stop it.
     */
    internal var reviveInvulnerableUntil: Float = reductionSource?.reviveInvulnerableUntil ?: 0f
    // ---- 0.6.0 STRUCTURES ----
    /** Structure walls, doors and signals of the window around the Core; an immutable value replaced on change. */
    internal var structureField: StructureField = reductionSource?.structureField ?: StructureField.EMPTY
    /** The structure the Core is inside (or was, within the leave radius), or null. Value-only. */
    internal var dungeon: DungeonState? = reductionSource?.dungeon
    /** Spent or collapsed structures, oldest first, at most [ContentBounds.MAX_SPENT_STRUCTURES]; id or flag. */
    internal var spentStructureIds: List<Long> = reductionSource?.spentStructureIds ?: emptyList()
    /** Last structure whose entry banner was shown (re-entering does not repeat it). */
    internal var lastAnnouncedStructureId: Long = reductionSource?.lastAnnouncedStructureId ?: 0L
    internal var dashBufferTime: Float = reductionSource?.dashBufferTime ?: 0f
    internal var bossSpawned: Boolean = reductionSource?.bossSpawned ?: false
    internal var keyboardBrakeActive: Boolean = reductionSource?.keyboardBrakeActive ?: false
    internal var secondaryBrakeActive: Boolean = reductionSource?.secondaryBrakeActive ?: false
    internal var touchBrakeActive: Boolean = reductionSource?.touchBrakeActive ?: false
    internal var uiScale: Float = reductionSource?.uiScale ?: 1f
    /** Device px per dp of the accepted viewport; the simulation itself runs in dp. */
    internal var viewportDensity: Float = reductionSource?.viewportDensity ?: 1f
    internal var accumulator: Float = reductionSource?.accumulator ?: 0f
    internal var lastTransitionSteps: Int = reductionSource?.lastTransitionSteps ?: 0
        internal set
    internal var previousCoreX: Float = reductionSource?.previousCoreX ?: 0f
    internal var previousCoreY: Float = reductionSource?.previousCoreY ?: 0f
    internal var previousSingularityX: Float = reductionSource?.previousSingularityX ?: 0f
    internal var previousSingularityY: Float = reductionSource?.previousSingularityY ?: 0f
    /** Camera at the start of the last fixed step; renderers interpolate toward [cameraX]. */
    internal var previousCameraX: Float = reductionSource?.previousCameraX ?: 0f
    internal var previousCameraY: Float = reductionSource?.previousCameraY ?: 0f
    /** Morningstar position at the start of the last fixed step (render interpolation only). */
    internal var previousMorningstarX: Float = reductionSource?.previousMorningstarX ?: 0f
    internal var previousMorningstarY: Float = reductionSource?.previousMorningstarY ?: 0f
    /**
     * Whether a fixed step has run since the simulation last entered RUNNING, so every previous
     * position is that step's start. Until then renderers present the exact state (render only).
     */
    internal var interpolationPrimed: Boolean = reductionSource?.interpolationPrimed ?: false
    internal var trailLastX: Float = reductionSource?.trailLastX ?: 0f
    internal var trailLastY: Float = reductionSource?.trailLastY ?: 0f
    internal var trailDistanceCarry: Float = reductionSource?.trailDistanceCarry ?: 0f
    internal var weaponClock: Float = reductionSource?.weaponClock ?: 0f
    internal var weaponSecondaryClock: Float = reductionSource?.weaponSecondaryClock ?: 0f
    internal var pendingLevelChoices: Int = reductionSource?.pendingLevelChoices ?: 0
    internal var pendingRelicChoices: Int = reductionSource?.pendingRelicChoices ?: 0
    internal var pendingBindingRelic: RelicId? = reductionSource?.pendingBindingRelic
    internal var pendingRelicBindAction: RelicChoiceAction? = reductionSource?.pendingRelicBindAction
    /**
     * The unowned active item taken while every slot held an active: it waits, with its Reborn roll, for the
     * [ChoiceType.ACTIVE_SLOT] prompt to replace a slot or salvage it; null otherwise.
     */
    internal var pendingActiveItemId: Int? = reductionSource?.pendingActiveItemId
    internal var pendingActiveReborn: Boolean = reductionSource?.pendingActiveReborn ?: false
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
    internal val echoHits: MutableList<EchoHit> = reductionSource?.echoHits
        ?.let { source ->
            if (shareStableReductionStorage) {
                source
            } else {
                source.mapTo(ArrayList(source.size), EchoHit::copy)
            }
        }
        ?: mutableListOf()
    internal val agonyMutationCounts: CopyOnWriteIntArray = reductionSource?.agonyMutationCounts?.let {
        if (shareStableReductionStorage) it else it.fork()
    }
        ?: CopyOnWriteIntArray(IntArray(content.weapons.size))
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
    // ---- 0.6.0 TERRAIN-P ----
    /** How straight the Core's heading has stayed (0..1); straight-line fatigue reads it. */
    internal var coreStraightness: Float = reductionSource?.coreStraightness ?: 0f
    internal var timeSinceDamage: Float = reductionSource?.timeSinceDamage ?: 0f
    internal var hurtCooldown: Float = reductionSource?.hurtCooldown ?: 0f
    internal var lastAimDirectionX: Float = reductionSource?.lastAimDirectionX ?: 1f
    internal var lastAimDirectionY: Float = reductionSource?.lastAimDirectionY ?: 0f
    /** Run Matter already banked; later grants in the same run bank only the difference. */
    internal var bankedRunMatter: Long = reductionSource?.bankedRunMatter ?: 0L
    internal var activeChoiceType: ChoiceType = reductionSource?.activeChoiceType ?: ChoiceType.ITEM

    var phase: GamePhase = reductionSource?.phase ?: GamePhase.RUNNING
        internal set
    var settings: PlayerPreferences = reductionSource?.settings ?: bootstrapProgress?.preferences ?: PlayerPreferences()
        internal set
    var rebirthLevel: Int = reductionSource?.rebirthLevel ?: activeRebirthProfile.tier
        internal set
    /**
     * Rebirth rank (one above the highest clear) from the bootstrap profile, never below the run's tier.
     * Unlock gates (items, Relics, Heirloom cards) read it; tier scaling and the roster read [rebirthLevel].
     */
    var rebirthRank: Int = reductionSource?.rebirthRank
        ?: max(activeRebirthProfile.tier, bootstrapProgress?.rebirthProgress?.rank ?: activeRebirthProfile.tier)
        internal set
    /** Volatile Remains bursts waiting to detonate (Rebirth R4), at most [MAX_VOLATILE_BURSTS]. */
    internal var volatileBursts: ImmutableList<VolatileBurstProjection> = reductionSource?.volatileBursts ?: immutableListOf()
    var screenWidth: Float = reductionSource?.screenWidth ?: 1280f
        internal set
    var screenHeight: Float = reductionSource?.screenHeight ?: 720f
        internal set

    var coreX: Float = reductionSource?.coreX ?: 0f
        internal set
    var coreY: Float = reductionSource?.coreY ?: 0f
        internal set
    /** Collision and draw radius of the Core; every simulation contact reads this, never [CORE_RADIUS]. */
    internal var coreRadius: Float = reductionSource?.coreRadius ?: CORE_RADIUS
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
    var nextLevelData: Int = reductionSource?.nextLevelData
        ?: content.tempo.dataRequiredForLevel(1, activeRebirthProfile)
        internal set
    var keys: Int = reductionSource?.keys ?: 0
        internal set
    var kills: Int = reductionSource?.kills ?: 0

    var damageDealt: Double = reductionSource?.damageDealt ?: 0.0
    var damageTaken: Double = reductionSource?.damageTaken ?: 0.0
    var damageAbsorbed: Double = reductionSource?.damageAbsorbed ?: 0.0
    var dataCollected: Long = reductionSource?.dataCollected ?: 0L
    var pickupsCollected: Long = reductionSource?.pickupsCollected ?: 0L
    var keysCollected: Long = reductionSource?.keysCollected ?: 0L
    var eliteKills: Int = reductionSource?.eliteKills ?: 0
    var bestCombo: Int = reductionSource?.bestCombo ?: 0
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
    var pickupRadius: Float = reductionSource?.pickupRadius ?: content.pickups.magnetRadius
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
    // ---- 0.6.0 TERRAIN-P ----
    /** The flight law's grip and sling build stats (raw; clamped where the law reads them). */
    internal var gripStrength: Float = reductionSource?.gripStrength ?: 1f
    internal var slingStrength: Float = reductionSource?.slingStrength ?: 1f
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
            ChoiceType.ITEM -> true
            // Offers come from owned weapons only: with no more than one offer's worth a reroll would deal the same set.
            ChoiceType.WEAPON -> weaponOfferCandidates().size > WEAPON_OFFER_CHOICES
            ChoiceType.RELIC -> !heirloomOfferActive
            ChoiceType.TOTEM, ChoiceType.RELIC_BIND, ChoiceType.ACTIVE_SLOT -> false
            // Omens and dimension boons are never rerolled; a signal prompt deals fixed options.
            ChoiceType.OMEN, ChoiceType.SIGNAL, ChoiceType.BOON -> false
        } && !dimensionRewardOpen()
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
        viewportDensity = density
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
