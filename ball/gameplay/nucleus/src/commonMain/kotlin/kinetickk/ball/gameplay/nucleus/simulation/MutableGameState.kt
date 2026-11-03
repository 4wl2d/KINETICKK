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
import kotlin.jvm.JvmInline
import kinetickk.ball.gameplay.nucleus.simulation.dimensions.*
import kinetickk.ball.gameplay.nucleus.simulation.progression.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*
import kinetickk.ball.gameplay.nucleus.simulation.skills.SkillCastState
import kinetickk.ball.gameplay.nucleus.simulation.elements.CoreReactionState
import kinetickk.ball.gameplay.nucleus.simulation.director.SpawnDirectorState
import kinetickk.ball.gameplay.nucleus.simulation.flight.CoreStasisState
import kinetickk.ball.gameplay.nucleus.simulation.builds.*

/**
 * Reducer-owned simulation state. Each field declares its initial value and its copy from [copySource] together,
 * so extending owned State requires no second field-copy list in the render mapper.
 *
 * The production tick mutates one instance in place. [deepCopy] isolates every mutable storage, random stream and
 * pending output buffer of its source; immutable values are shared.
 *
 * Cohesive groups with a single writing domain live in state holders, declared first so every later initializer sees
 * them; each holder copies itself from its [copySource] counterpart. New fields go into a holder, read as
 * `state.<holder>.<field>`; the forwarders in MutableGameStateForwarders.kt only keep older call sites unchanged.
 */
internal class MutableGameState(
    internal val content: GameplayContentSnapshot,
    seed: Int = 731_991,
    initialMatter: Int? = null,
    initialRebirthLevel: Int = 0,
    bootstrapProgress: GameplayProfileSnapshot? = null,
    copySource: MutableGameState? = null,
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

    /** Per-step combat and elements scratch. */
    internal val scratch: StepScratch = StepScratch(copySource?.scratch)
    /** Choices waiting to open. */
    internal val pendingChoice: PendingChoiceState = PendingChoiceState(copySource?.pendingChoice)
    /** Skill and Apex input buffers and the running cast's scratch. */
    internal val skillCast: SkillCastState = SkillCastState(copySource?.skillCast)
    /** Lingering reaction effects on the Core. */
    internal val coreReactions: CoreReactionState = CoreReactionState(copySource?.coreReactions)
    /** Retained relic mechanic timers, charges and counters. */
    internal val relicMechanics: RelicMechanicState = RelicMechanicState(copySource?.relicMechanics)
    /** Spawn pacing, director plan and live encounters. */
    internal val spawnDirector: SpawnDirectorState = SpawnDirectorState(content, copySource?.spawnDirector)
    /** Structure window, dungeon and spent structures. */
    internal val structures: StructureRunState = StructureRunState(copySource?.structures)
    /** Core Stasis: the leash anchor, speed average and stillness clocks. */
    internal val coreStasis: CoreStasisState = CoreStasisState(copySource?.coreStasis)

    /** Profile capture, run options and the draft deck. */
    internal val profileCapture: RunProfileCapture = RunProfileCapture(
        content,
        copySource?.profileCapture,
        seed,
        initialRebirthLevel,
        bootstrapProgress,
        runMode,
        offerRunOmen,
    )

    /** Known and pending discoveries. */
    internal val discoveryState: DiscoveryState = DiscoveryState(content, copySource?.discoveryState, bootstrapProgress)

    internal var gameplayRandom: CloneableXorWowRandom = copySource?.gameplayRandom?.copy()
        ?: CloneableXorWowRandom(seed)
    /** Offers draw from their own stream, so drafting choices never shift spawns or crits. */
    internal var rewardRandom: CloneableXorWowRandom = copySource?.rewardRandom?.copy()
        ?: CloneableXorWowRandom(seed xor REWARD_SEED_SALT)
    /**
     * Every spawn, spawn position, enemy type, Elite and Herald aspect and director draw comes from this
     * stream, so spawning never shifts crits and procs, and a different run seed reshapes the waves.
     */
    internal var spawnRandom: CloneableXorWowRandom = copySource?.spawnRandom?.copy()
        ?: CloneableXorWowRandom(seed xor SPAWN_SEED_SALT)
    /** Folded Matrix effects of [matrixRanks]; immutable, refolded when a run starts. */
    internal var matrix: MatrixModifiers = copySource?.matrix ?: foldMatrix()
    /** The selected character's Refit level, fixed when a run starts. */
    internal var characterUpgradeLevel: Int = copySource?.characterUpgradeLevel ?: 0
    /** Refit's character-mechanic damage multiplier, fixed when a run starts. */
    internal var characterUpgradePower: Float = copySource?.characterUpgradePower ?: 1f
    internal val pendingDiscoveredItemIds: PendingDiscoveredItemIdBuffer
        get() = PendingDiscoveredItemIdBuffer(this)
    internal val itemStacks: RunIntArray = copySource?.itemStacks?.copy()
        ?: RunIntArray(IntArray(content.items.size))
    internal val rebornItems: RunIntArray = copySource?.rebornItems?.copy()
        ?: RunIntArray(IntArray(content.items.size))
    /**
     * Build gains in order: item ids at first acquisition (a replaced active item's id leaves it) and relic binds or
     * rank-three upgrades as [relicGain] entries. Decides affinity ties. Immutable value, replaced on change.
     */
    internal var acquisitionOrder: List<Int> = copySource?.acquisitionOrder ?: emptyList()
    /**
     * First acquisitions not yet reported to the Profile's recent-item history, oldest first; immutable and replaced on
     * change, at most [MAX_RECENT_ITEM_IDS] (the oldest drop first).
     */
    internal var pendingAcquiredItemIds: List<Int> = copySource?.pendingAcquiredItemIds ?: emptyList()
    /** Baby mode (RunMode.EASY), fixed when the run is created; a run never changes it. */
    internal val easyMode: Boolean = copySource?.easyMode ?: (runMode == RunMode.EASY)
    /** Immutable compiled build shared by reduction siblings; replaced by [recompileBuild]. */
    internal var build: CompiledBuild = copySource?.build ?: CompiledBuild.empty(content)
    internal val clauseReadyAt: RunFloatArray = copySource?.clauseReadyAt?.copy()
        ?: RunFloatArray(FloatArray(content.totalClauseCount))
    internal val clauseCounters: RunIntArray = copySource?.clauseCounters?.copy()
        ?: RunIntArray(IntArray(content.totalClauseCount))
    /** Fractional status stacks per item, so stack magnitudes apply deterministically without RNG. */
    internal val statusAccumulators: RunFloatArray = copySource?.statusAccumulators?.copy()
        ?: RunFloatArray(FloatArray(content.items.size))
    /** Earliest time each item may apply statuses again (per-item rate bound). */
    internal val itemStatusReadyAt: RunFloatArray = copySource?.itemStatusReadyAt?.copy()
        ?: RunFloatArray(FloatArray(content.items.size))
    internal var buffs: List<ActiveBuff> = copySource?.buffs ?: emptyList()
    /** Lingering fields; a deep copy forks the table. */
    internal val combatFieldTable: CombatFieldTable = copySource?.combatFieldTable?.fork() ?: CombatFieldTable()
    /** Field kinds the Core stood in at the last field pass, one bit per [CombatFieldKind] ordinal. */
    internal var coreFieldMask: Int = copySource?.coreFieldMask ?: 0
    /** When entering a field may next count as a field entry. */
    internal var fieldEnterReadyAt: Float = copySource?.fieldEnterReadyAt ?: 0f
    /** What the skill slots hold: the owned active items' effects; immutable, replaced when an active item binds, ranks or is replaced. */
    internal var skillLoadout: SkillLoadout = copySource?.skillLoadout ?: SkillLoadout.EMPTY
    /** Skill and Apex timing; a deep copy forks it. */
    internal var skillRuntime: SkillRuntime = copySource?.skillRuntime?.fork() ?: SkillRuntime()
    internal var reactionTokens: Float = copySource?.reactionTokens ?: REACTION_BURST
    internal var breakTokens: Float = copySource?.breakTokens ?: BREAK_BURST
    internal var heirloomCandidates: List<RelicId> = copySource?.heirloomCandidates
        ?: bootstrapProgress?.collection?.heirloomRelicIds?.toList().orEmpty()
    internal var heirloomPending: Boolean = copySource?.heirloomPending ?: false
    internal var heirloomOfferActive: Boolean = copySource?.heirloomOfferActive ?: false
    internal var pendingHeirloomResolved: Boolean = copySource?.pendingHeirloomResolved ?: false
    internal var pendingVictoryRelics: List<RelicId>? = copySource?.pendingVictoryRelics
    internal var severedRelics: List<SeveredRelic> = copySource?.severedRelics ?: emptyList()
    internal var ouroborosSpent: Boolean = copySource?.ouroborosSpent ?: false
    internal var ouroborosLockedUntil: Float = copySource?.ouroborosLockedUntil ?: 0f
    internal var invulnerableUntil: Float = copySource?.invulnerableUntil ?: 0f
    internal var overdriveBurnoutUntil: Float = copySource?.overdriveBurnoutUntil ?: 0f
    internal var overheatArmed: Boolean = copySource?.overheatArmed ?: true
    internal var wasBraking: Boolean = copySource?.wasBraking ?: false
    internal var brakeHoldTime: Float = copySource?.brakeHoldTime ?: 0f
    internal var brakeShedSpeed: Float = copySource?.brakeShedSpeed ?: 0f
    internal var critMissStreak: Int = copySource?.critMissStreak ?: 0
    internal var freeDashPrimed: Boolean = copySource?.freeDashPrimed ?: false
    internal var freeDashReadyAt: Float = copySource?.freeDashReadyAt ?: 0f
    internal var comboKillCounter: Int = copySource?.comboKillCounter ?: 0
    internal var offerPity: Int = copySource?.offerPity ?: 0
    /** Item offers opened this run; the Matrix widens every n-th one. */
    internal var itemOffersOpened: Int = copySource?.itemOffersOpened ?: 0
    internal var dashHitThisDash: Boolean = copySource?.dashHitThisDash ?: false
    internal var clauseDepth: Int = copySource?.clauseDepth ?: 0
    internal var arsenalCounter: Int = copySource?.arsenalCounter ?: 0
    internal var soundCueStorage: MutableList<GameplayAudioCue>? =
        copySource?.soundCueStorage?.let { source -> ArrayList(source) }
    internal val soundCues: PendingSoundCueBuffer
        get() = PendingSoundCueBuffer(this)
    internal var visualFxCueStorage: BoundedVisualFxCueAccumulator? =
        copySource?.visualFxCueStorage?.copy()
    internal val visualFxCues: PendingVisualFxCueBuffer
        get() = PendingVisualFxCueBuffer(this)
    internal var pendingBankedMatter: Long = copySource?.pendingBankedMatter ?: 0L
    internal var pendingClearedRebirthLevel: Int? = copySource?.pendingClearedRebirthLevel
    /** Mutable character state; a deep copy forks it (scalars and buffers copied). */
    internal var characterRuntime: CharacterRuntime = copySource?.characterRuntime?.fork() ?: CharacterRuntime()
    /** Owned state of the equipped weapon (entities, proc gates, element cadences, scaling inputs); a deep copy forks it. */
    internal var weaponRuntime: WeaponRuntime = copySource?.weaponRuntime?.fork() ?: WeaponRuntime()
    internal var pendingEliteKills: Int = copySource?.pendingEliteKills ?: 0
    internal var pendingDashHits: Int = copySource?.pendingDashHits ?: 0
    internal var pendingCompletedOrbits: Int = copySource?.pendingCompletedOrbits ?: 0
    internal var pendingArchitectDefeatedWith: CoreShape? = copySource?.pendingArchitectDefeatedWith
    /**
     * Cumulative achievement counters awaiting publication. They ride along with any other progress
     * update and are otherwise published once the run banks (exit, death, victory), so a kill or a
     * pickup never forces a Profile write on its own.
     */
    internal var pendingTotalKills: Int = copySource?.pendingTotalKills ?: 0
    /** Hard obstacle bounces awaiting publication; the obstacle system counts them. */
    internal var pendingRicochets: Int = copySource?.pendingRicochets ?: 0
    internal var pendingDataPickups: Int = copySource?.pendingDataPickups ?: 0
    /** The run banked, so the next progress update also publishes the deferred counters. */
    internal var achievementFlushDue: Boolean = copySource?.achievementFlushDue ?: false
    /** Window sites: the two offers of a window, or the one active trial. Immutable, replaced on change. */
    internal var sites: List<SiteState> = copySource?.sites ?: emptyList()
    internal var nextSiteWindowIndex: Int = copySource?.nextSiteWindowIndex ?: 0
    internal var pendingDirectedRewards: List<kinetickk.ball.content.api.DirectedReward> = copySource?.pendingDirectedRewards ?: emptyList()
    internal var directedReward: kinetickk.ball.content.api.DirectedReward? = copySource?.directedReward
    internal var selectedRewardFocus: kinetickk.ball.content.api.RewardFocus? = copySource?.selectedRewardFocus
    /** Window kinds draw from their own stream, so sites never shift offers, spawns or crits. */
    internal var siteRandom: CloneableXorWowRandom = copySource?.siteRandom?.copy()
        ?: CloneableXorWowRandom(seed xor SITE_SEED_SALT)
    internal var ambientSites: AmbientSiteWindow = copySource?.ambientSites ?: AmbientSiteWindow.EMPTY
    internal var secretSigil: SiteState? = copySource?.secretSigil
    /** Ambient sites opened this run, oldest first, at most [ContentBounds.MAX_OPENED_SITES]. */
    internal var openedSiteIds: List<Long> = copySource?.openedSiteIds ?: emptyList()
    /** [RunSecretFlag] bits of this run. */
    internal var runSecretFlags: Int = copySource?.runSecretFlags ?: 0
    /** Rarity-floor item offers waiting, at most the site policy's bound. */
    internal var pendingFloorOffers: List<ItemRarity> = copySource?.pendingFloorOffers ?: emptyList()
    /** The Golden Drifter alive (−1: none) and the run second it escapes. */
    internal var goldenDrifterId: Int = copySource?.goldenDrifterId ?: -1
    internal var goldenDrifterUntil: Float = copySource?.goldenDrifterUntil ?: 0f
    /** Spin Cycle: completed one-second buckets of signed rotation, the running bucket, its clock and last angle. */
    internal var spinBuckets: List<Float> = copySource?.spinBuckets ?: emptyList()
    internal var spinCurrent: Float = copySource?.spinCurrent ?: 0f
    internal var spinClock: Float = copySource?.spinClock ?: 0f
    internal var spinLastAngle: Float = copySource?.spinLastAngle ?: 0f
    internal var spinPrimed: Boolean = copySource?.spinPrimed ?: false
    internal var omenPending: Boolean = copySource?.omenPending ?: false
    internal var runOmen: OmenId? = copySource?.runOmen
    /** Rebirth mutators a run modifier added (bit per ordinal), on top of the tier's own. */
    internal var runMutatorMask: Int = copySource?.runMutatorMask ?: 0
    internal var directorPersonality: DirectorPersonalityId? = copySource?.directorPersonality
    internal var featuredAspect: Int = copySource?.featuredAspect ?: -1
    internal var surgeIndex: Int = copySource?.surgeIndex ?: 0
    internal var surgeThemeType: Int = copySource?.surgeThemeType ?: -1
    internal var varietyAnnouncement: Int = copySource?.varietyAnnouncement ?: 0
    internal var keynoteTag: BuildTag? = copySource?.keynoteTag
    internal var keynoteElement: StatusElement? = copySource?.keynoteElement
    /** At most [ContentBounds.MAX_WORLD_EVENTS_PER_RUN] plans. */
    internal var worldEventPlans: ImmutableList<WorldEventPlan> = copySource?.worldEventPlans ?: immutableListOf()
    internal var nextWorldEvent: Int = copySource?.nextWorldEvent ?: 0
    internal var worldEvent: WorldEventProjection? = copySource?.worldEvent
    internal var worldEventPulse: Int = copySource?.worldEventPulse ?: 0
    internal var worldEventNextPulseAt: Float = copySource?.worldEventNextPulseAt ?: 0f
    internal var eventSignals: ImmutableList<EventSignalProjection> = copySource?.eventSignals ?: immutableListOf()
    internal var nextSignalSlot: Int = copySource?.nextSignalSlot ?: 0
    internal var signalsSpawned: Int = copySource?.signalsSpawned ?: 0
    internal var signalsEngaged: Int = copySource?.signalsEngaged ?: 0
    /** At most [ContentBounds.MAX_PENDING_SIGNAL_PROMPTS] prompts. */
    internal var pendingSignalPrompts: ImmutableList<SignalPrompt> = copySource?.pendingSignalPrompts ?: immutableListOf()
    /** The open item offer's rarity floor (null: none); rerolls keep it, finishing the choice clears it. */
    internal var offerRarityFloor: ItemRarity? = copySource?.offerRarityFloor

    internal var nextEntityId: Int = copySource?.nextEntityId ?: 1
    /** Seed of this run; obstacle chunks hash it with their absolute chunk. */
    internal val runSeed: Int = copySource?.runSeed ?: seed
    /** Content and seed are fixed for this state and all reduction siblings; rank the template pool only once. */
    internal val structureTemplatePool: Int = copySource?.structureTemplatePool
        ?: structureTemplateMask(content.structures, runSeed)
    /** Accumulated rebase shift: an absolute world position is the run-local position plus this origin. */
    internal var worldOriginX: Double = copySource?.worldOriginX ?: 0.0
    internal var worldOriginY: Double = copySource?.worldOriginY ?: 0.0
    /** Generated obstacle window around the Core; an immutable value replaced on change. */
    internal var obstacleField: ObstacleField = copySource?.obstacleField ?: ObstacleField.EMPTY
    /** Destroyed unstable asteroids, oldest first, at most [ContentBounds.MAX_DESTROYED_OBSTACLES]. */
    internal var destroyedObstacleIds: List<Long> = copySource?.destroyedObstacleIds ?: emptyList()
    /** The run's world layer; only a layer switch writes it. */
    internal var worldLayer: WorldLayer = copySource?.worldLayer ?: WorldLayer.BASE
    /** The active keep-out of the current layer's generation (null: the origin safe radius applies). */
    internal var worldKeepOut: WorldKeepOut? = copySource?.worldKeepOut
    /** Run boons owned, in grant order, at most [ContentBounds.MAX_RUN_BOONS]. */
    internal var runBoons: ImmutableList<Int> = copySource?.runBoons ?: immutableListOf()
    /** Rift offers, visited and completed layers, the open glitch rift, the visit and its pending rewards; a value. */
    internal var dimensionRun: DimensionRunState = copySource?.dimensionRun ?: DimensionRunState.EMPTY
    /** Seconds the Core has flown below the layer's pace without a break (Nadir heat). */
    internal var dimensionSlowSeconds: Float = copySource?.dimensionSlowSeconds ?: 0f
    /** Seconds toward the next scorch tick once the heat grace ran out. */
    internal var dimensionScorchClock: Float = copySource?.dimensionScorchClock ?: 0f
    /** Visit time on the layer's own clock (the Archive's runs with the Core's speed). */
    internal var dimensionLocalClock: Float = copySource?.dimensionLocalClock ?: 0f
    /** The layer whose reward the open choice is (null: the open choice is no dimension reward). */
    internal var dimensionRewardLayer: WorldLayer? = copySource?.dimensionRewardLayer
    /** Run time before which an asteroid collision deals no further damage and fires no ricochet. */
    internal var obstacleHitReadyAt: Float = copySource?.obstacleHitReadyAt ?: 0f
    /** The solid surface the last hurting collision struck ([NO_SURFACE_ID]: none). */
    internal var obstacleHitSurfaceId: Long = copySource?.obstacleHitSurfaceId ?: NO_SURFACE_ID
    /** Run time before which Near Miss grants no further Overdrive. */
    internal var nearMissReadyAt: Float = copySource?.nearMissReadyAt ?: 0f
    /** Gap from the Core's surface to the nearest solid obstacle surface within reach; +∞ when none (NearObstacle). */
    internal var coreObstacleGap: Float = copySource?.coreObstacleGap ?: Float.POSITIVE_INFINITY
    /** Run time before which GRAZE cannot fire again. */
    internal var grazeReadyAt: Float = copySource?.grazeReadyAt ?: 0f
    /** The gravity knot whose influence holds the Core ([NO_OBSTACLE_ID] outside) and the heading it entered with. */
    internal var slingWellId: Long = copySource?.slingWellId ?: NO_OBSTACLE_ID
    internal var slingEntryHeading: Float = copySource?.slingEntryHeading ?: 0f
    /**
     * End of the invulnerability a revive (Second Wind, Ouroboros) granted: the only window a lethal
     * collision honours; other invulnerability (Glass Comet, Bulwark) does not stop it.
     */
    internal var reviveInvulnerableUntil: Float = copySource?.reviveInvulnerableUntil ?: 0f
    internal var dashBufferTime: Float = copySource?.dashBufferTime ?: 0f
    internal var bossSpawned: Boolean = copySource?.bossSpawned ?: false
    internal var keyboardBrakeActive: Boolean = copySource?.keyboardBrakeActive ?: false
    internal var secondaryBrakeActive: Boolean = copySource?.secondaryBrakeActive ?: false
    internal var touchBrakeActive: Boolean = copySource?.touchBrakeActive ?: false
    internal var uiScale: Float = copySource?.uiScale ?: 1f
    /** Device px per dp of the accepted viewport; the simulation itself runs in dp. */
    internal var viewportDensity: Float = copySource?.viewportDensity ?: 1f
    internal var accumulator: Float = copySource?.accumulator ?: 0f
    internal var lastTransitionSteps: Int = copySource?.lastTransitionSteps ?: 0
        internal set
    internal var previousCoreX: Float = copySource?.previousCoreX ?: 0f
    internal var previousCoreY: Float = copySource?.previousCoreY ?: 0f
    internal var previousSingularityX: Float = copySource?.previousSingularityX ?: 0f
    internal var previousSingularityY: Float = copySource?.previousSingularityY ?: 0f
    /** Camera at the start of the last fixed step; renderers interpolate toward [cameraX]. */
    internal var previousCameraX: Float = copySource?.previousCameraX ?: 0f
    internal var previousCameraY: Float = copySource?.previousCameraY ?: 0f
    /** Morningstar position at the start of the last fixed step (render interpolation only). */
    internal var previousMorningstarX: Float = copySource?.previousMorningstarX ?: 0f
    internal var previousMorningstarY: Float = copySource?.previousMorningstarY ?: 0f
    /**
     * Whether a fixed step has run since the simulation last entered RUNNING, so every previous
     * position is that step's start. Until then renderers present the exact state (render only).
     */
    internal var interpolationPrimed: Boolean = copySource?.interpolationPrimed ?: false
    internal var trailLastX: Float = copySource?.trailLastX ?: 0f
    internal var trailLastY: Float = copySource?.trailLastY ?: 0f
    internal var trailDistanceCarry: Float = copySource?.trailDistanceCarry ?: 0f
    internal var weaponClock: Float = copySource?.weaponClock ?: 0f
    internal var weaponSecondaryClock: Float = copySource?.weaponSecondaryClock ?: 0f
    internal val relicRanks: RunIntArray = copySource?.relicRanks?.copy()
        ?: RunIntArray(IntArray(content.relics.size))
    internal val relicCooldowns: RunFloatArray = copySource?.relicCooldowns?.copy()
        ?: RunFloatArray(FloatArray(content.relics.size))
    internal val relicCounters: RunIntArray = copySource?.relicCounters?.copy()
        ?: RunIntArray(IntArray(content.relics.size))
    internal val echoHits: MutableList<EchoHit> = copySource?.echoHits
        ?.let { source ->
            source.mapTo(ArrayList(source.size), EchoHit::copy)
        }
        ?: mutableListOf()
    internal val agonyMutationCounts: RunIntArray = copySource?.agonyMutationCounts?.copy()
        ?: RunIntArray(IntArray(content.weapons.size))
    internal var dataFraction: Float = copySource?.dataFraction ?: 0f
    internal var matterFraction: Float = copySource?.matterFraction ?: 0f
    internal var shieldRechargeDelay: Float = copySource?.shieldRechargeDelay ?: 0f
    internal var overheatHoldTime: Float = copySource?.overheatHoldTime ?: 0f
    internal var saturationHeadingX: Float = copySource?.saturationHeadingX ?: 1f
    internal var saturationHeadingY: Float = copySource?.saturationHeadingY ?: 0f
    internal var smoothedVelocityX: Float = copySource?.smoothedVelocityX ?: 0f
    internal var smoothedVelocityY: Float = copySource?.smoothedVelocityY ?: 0f
    internal var turnHeadingEstablished: Boolean = copySource?.turnHeadingEstablished ?: false
    internal var turnHoldTime: Float = copySource?.turnHoldTime ?: 0f
    internal var turnRecoveryCooldown: Float = copySource?.turnRecoveryCooldown ?: 0f
    internal var turnDirection: Int = copySource?.turnDirection ?: 0
    /** How straight the Core's heading has stayed (0..1); straight-line fatigue reads it. */
    internal var coreStraightness: Float = copySource?.coreStraightness ?: 0f
    internal var timeSinceDamage: Float = copySource?.timeSinceDamage ?: 0f
    internal var hurtCooldown: Float = copySource?.hurtCooldown ?: 0f
    internal var lastAimDirectionX: Float = copySource?.lastAimDirectionX ?: 1f
    internal var lastAimDirectionY: Float = copySource?.lastAimDirectionY ?: 0f
    /** Run Matter already banked; later grants in the same run bank only the difference. */
    internal var bankedRunMatter: Long = copySource?.bankedRunMatter ?: 0L
    internal var activeChoiceType: ChoiceType = copySource?.activeChoiceType ?: ChoiceType.ITEM

    var phase: GamePhase = copySource?.phase ?: GamePhase.RUNNING
        internal set
    var settings: PlayerPreferences = copySource?.settings ?: bootstrapProgress?.preferences ?: PlayerPreferences()
        internal set
    var rebirthLevel: Int = copySource?.rebirthLevel ?: activeRebirthProfile.tier
        internal set
    /**
     * Rebirth rank (one above the highest clear) from the bootstrap profile, never below the run's tier.
     * Unlock gates (items, Relics, Heirloom cards) read it; tier scaling and the roster read [rebirthLevel].
     */
    var rebirthRank: Int = copySource?.rebirthRank
        ?: max(activeRebirthProfile.tier, bootstrapProgress?.rebirthProgress?.rank ?: activeRebirthProfile.tier)
        internal set
    /** Volatile Remains bursts waiting to detonate (Rebirth R4), at most [MAX_VOLATILE_BURSTS]. */
    internal var volatileBursts: ImmutableList<VolatileBurstProjection> = copySource?.volatileBursts ?: immutableListOf()
    var screenWidth: Float = copySource?.screenWidth ?: 1280f
        internal set
    var screenHeight: Float = copySource?.screenHeight ?: 720f
        internal set

    var coreX: Float = copySource?.coreX ?: 0f
        internal set
    var coreY: Float = copySource?.coreY ?: 0f
        internal set
    /** Collision and draw radius of the Core; every simulation contact reads this, never [CORE_RADIUS]. */
    internal var coreRadius: Float = copySource?.coreRadius ?: CORE_RADIUS
    var velocityX: Float = copySource?.velocityX ?: 0f
        internal set
    var velocityY: Float = copySource?.velocityY ?: 0f
        internal set
    var cameraX: Float = copySource?.cameraX ?: 0f
        internal set
    var cameraY: Float = copySource?.cameraY ?: 0f
        internal set
    var pointerX: Float = copySource?.pointerX ?: 900f
        internal set
    var pointerY: Float = copySource?.pointerY ?: 360f
        internal set
    var pointerActive: Boolean = copySource?.pointerActive ?: true
        internal set
    var braking: Boolean = copySource?.braking ?: false
        internal set

    var elapsed: Float = copySource?.elapsed ?: 0f
        internal set
    var heat: Float = copySource?.heat ?: 0f
        internal set
    var overheated: Boolean = copySource?.overheated ?: false
        internal set
    var dashPhaseTime: Float = copySource?.dashPhaseTime ?: 0f
        internal set
    var hp: Float = copySource?.hp ?: 100f
        internal set
    var maxHp: Float = copySource?.maxHp ?: 100f
        internal set
    var shield: Float = copySource?.shield ?: 0f
        internal set
    var maxShield: Float = copySource?.maxShield ?: 0f
        internal set
    var level: Int = copySource?.level ?: 1
        internal set
    var data: Int = copySource?.data ?: 0
        internal set
    var nextLevelData: Int = copySource?.nextLevelData
        ?: content.tempo.dataRequiredForLevel(1, activeRebirthProfile)
        internal set
    var keys: Int = copySource?.keys ?: 0
        internal set
    var kills: Int = copySource?.kills ?: 0

    var damageDealt: Double = copySource?.damageDealt ?: 0.0
    var damageTaken: Double = copySource?.damageTaken ?: 0.0
    var damageAbsorbed: Double = copySource?.damageAbsorbed ?: 0.0
    var dataCollected: Long = copySource?.dataCollected ?: 0L
    var pickupsCollected: Long = copySource?.pickupsCollected ?: 0L
    var keysCollected: Long = copySource?.keysCollected ?: 0L
    var eliteKills: Int = copySource?.eliteKills ?: 0
    var bestCombo: Int = copySource?.bestCombo ?: 0
        internal set
    var combo: Int = copySource?.combo ?: 0
        internal set
    var comboTime: Float = copySource?.comboTime ?: 0f
        internal set
    var runMatter: Long = copySource?.runMatter ?: 0L
        internal set
    var totalMatter: Long = copySource?.totalMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.matter ?: 0L
        internal set
    var lifetimeMatter: Long = copySource?.lifetimeMatter ?: initialMatter?.toLong()
        ?: bootstrapProgress?.economy?.lifetimeMatter ?: totalMatter
        internal set
    var lastImpact: Float = copySource?.lastImpact ?: 0f
        internal set
    var lastImpactTime: Float = copySource?.lastImpactTime ?: 0f
        internal set
    var damageFlash: Float = copySource?.damageFlash ?: 0f
        internal set
    var runGrace: Float = copySource?.runGrace ?: 0f
        internal set
    var screenShake: Float = copySource?.screenShake ?: 0f
        internal set
    var message: String = copySource?.message ?: ""
        internal set
    var messageTime: Float = copySource?.messageTime ?: 0f
        internal set

    var mass: Float = copySource?.mass ?: 1f
        internal set
    var damageMultiplier: Float = copySource?.damageMultiplier ?: 1f
        internal set
    var weaponPower: Float = copySource?.weaponPower ?: 1f
        internal set
    var coolingRate: Float = copySource?.coolingRate ?: 19f
        internal set
    var magnetStrength: Float = copySource?.magnetStrength ?: 4.65f
        internal set
    var dashImpulse: Float = copySource?.dashImpulse ?: 590f
        internal set
    var dashHeatCost: Float = copySource?.dashHeatCost ?: 36f
        internal set
    var regenPerSecond: Float = copySource?.regenPerSecond ?: 0f
        internal set
    var critChance: Float = copySource?.critChance ?: 0.05f
        internal set
    var critMultiplier: Float = copySource?.critMultiplier ?: 1.5f
        internal set
    var pickupRadius: Float = copySource?.pickupRadius ?: content.pickups.magnetRadius
        internal set
    var luck: Float = copySource?.luck ?: 0f
        internal set
    var dataGain: Float = copySource?.dataGain ?: 1f
        internal set
    var matterGain: Float = copySource?.matterGain ?: 1f
        internal set
    var attackSpeed: Float = copySource?.attackSpeed ?: 1f
        internal set
    var damageReduction: Float = copySource?.damageReduction ?: 0f
        internal set
    var comboWindow: Float = copySource?.comboWindow ?: 2.8f
        internal set
    var overdriveGain: Float = copySource?.overdriveGain ?: 1f
        internal set
    var dragCoefficient: Float = copySource?.dragCoefficient ?: 0.29f
        internal set
    /** The flight law's grip and sling build stats (raw; clamped where the law reads them). */
    internal var gripStrength: Float = copySource?.gripStrength ?: 1f
    internal var slingStrength: Float = copySource?.slingStrength ?: 1f
    var polarityStability: Float = copySource?.polarityStability ?: 1f
        internal set

    var weapon: WeaponId = copySource?.weapon
        ?: bootstrapProgress?.loadout?.selectedWeapon ?: WeaponId.FLUX_WAKE
        internal set
    var startingWeapon: WeaponId = copySource?.startingWeapon ?: weapon
        internal set
    var weaponLevel: Int = copySource?.weaponLevel ?: 1
        internal set
    var overdriveCharge: Float = copySource?.overdriveCharge ?: 0f
        internal set
    var overdriveTime: Float = copySource?.overdriveTime ?: 0f
        internal set
    var rerollsRemaining: Int = copySource?.rerollsRemaining ?: 1
        internal set
    var acquiredItemCount: Int = copySource?.acquiredItemCount ?: 0
        internal set
    var recentItem: ItemDefinition? = copySource?.recentItem
        internal set
    var equippedRelics: List<EquippedRelic> = copySource?.equippedRelics ?: emptyList()
        internal set

    var morningstarAngle: Float = copySource?.morningstarAngle ?: 0f
        internal set
    var morningstarX: Float = copySource?.morningstarX ?: 0f
        internal set
    var morningstarY: Float = copySource?.morningstarY ?: 0f
        internal set
    var weaponBeamTime: Float = copySource?.weaponBeamTime ?: 0f
        internal set
    var weaponBeamStartX: Float = copySource?.weaponBeamStartX ?: 0f
        internal set
    var weaponBeamStartY: Float = copySource?.weaponBeamStartY ?: 0f
        internal set
    var weaponBeamEndX: Float = copySource?.weaponBeamEndX ?: 0f
        internal set
    var weaponBeamEndY: Float = copySource?.weaponBeamEndY ?: 0f
        internal set

    var coreShape: CoreShape = copySource?.coreShape
        ?: bootstrapProgress?.loadout?.coreShape ?: CoreShape.ORB
        internal set

    val enemies: MutableList<Enemy> = copySource?.enemies?.let { source ->
        source.mapTo(ArrayList(source.size), Enemy::isolatedCopy)
    } ?: mutableListOf()
    val projectiles: MutableList<Projectile> = copySource?.projectiles?.let { source ->
        source.mapTo(ArrayList(source.size), Projectile::isolatedCopy)
    }
        ?: mutableListOf()
    val pickups: MutableList<Pickup> = copySource?.pickups?.let { source ->
        source.mapTo(ArrayList(source.size), Pickup::copy)
    }
        ?: mutableListOf()
    val trail: MutableList<TrailPoint> = copySource?.trail?.let { source ->
        source.mapTo(ArrayList(source.size), TrailPoint::copy)
    }
        ?: mutableListOf()
    val weaponNodes: MutableList<WeaponNode> = copySource?.weaponNodes?.let { source ->
        source.mapTo(ArrayList(source.size), WeaponNode::copy)
    }
        ?: mutableListOf()
    val weaponOrbitals: MutableList<WeaponOrbital> = copySource?.weaponOrbitals?.let { source ->
        source.mapTo(ArrayList(source.size), WeaponOrbital::copy)
    }
        ?: mutableListOf()
    var choices: List<ChoiceOption> = copySource?.choices ?: emptyList()
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
        if (copySource == null && startingWeapon !in unlockedWeaponSet) {
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

/**
 * The one explicit deep copy: reward previews, lifecycle commands, a tick while progress is pending and the pure
 * test entry run on it, so the source is never mutated through it.
 */
internal fun MutableGameState.deepCopy(): MutableGameState =
    MutableGameState(content = content, copySource = this)

/**
 * Allocation-free owner view over discoveries awaiting publication.
 *
 * The actual set is absent for the overwhelmingly common empty state. A deep copy only copies it when
 * there is retained output to preserve.
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
        owner.pendingDiscoveredItemIdStorage = RunMutableSet(mutableSetOf(itemId))
        return true
    }

    operator fun plusAssign(itemId: Int) { add(itemId) }

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

    operator fun contains(cue: GameplayAudioCue): Boolean = owner.soundCueStorage?.contains(cue) == true

    fun add(cue: GameplayAudioCue): Boolean {
        val retained = owner.soundCueStorage
        if (retained != null) return retained.add(cue)
        owner.soundCueStorage = arrayListOf(cue)
        return true
    }

    operator fun plusAssign(cue: GameplayAudioCue) { add(cue) }

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
