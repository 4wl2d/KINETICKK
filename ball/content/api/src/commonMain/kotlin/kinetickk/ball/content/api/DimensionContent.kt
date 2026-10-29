// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

/** The run's world layer. Ordinal = obstacle-id layer tag (bits 60..62), so at most 8 entries; append only. */
enum class WorldLayer(val habitat: EnemyHabitat) {
    BASE(EnemyHabitat.SPACE),
    ZENITH(EnemyHabitat.ZENITH),
    NADIR(EnemyHabitat.NADIR),
    ARCHIVE(EnemyHabitat.ARCHIVE),
    ;

    val bit: Int get() = 1 shl ordinal
}

/**
 * One objective per visit; [layer] is the only layer that may roll it. [boss] is the kind a boss objective spawns
 * and must defeat (null for a course or a collection); the bootstrap checks its rank and habitat against the roster.
 */
enum class DimensionObjective(val layer: WorldLayer, val label: String, val instruction: String, val boss: EnemyType? = null) {
    ASCENT(
        WorldLayer.ZENITH, "Ascent",
        "Fly through the halo gates in order, faster than cruising. Reach each gate before its time runs out.",
    ),
    JUDGEMENT(WorldLayer.ZENITH, "Judgement", "Defeat the Throne.", EnemyType.THRONE),
    HARVEST(
        WorldLayer.NADIR, "Harvest",
        "Collect twenty embers thrown from burning enemies. Keep moving: slow flight scorches the Core.",
    ),
    PYRE(WorldLayer.NADIR, "Pyre", "Defeat the Pyre Herald. Keep moving: slow flight scorches the Core.", EnemyType.PYRE_HERALD),
    ANTECEDENT(WorldLayer.ARCHIVE, "Antecedent", "Defeat the Antecedent. Time moves only when you move.", EnemyType.ANTECEDENT),
}

enum class DimensionRewardKind { NONE, BLESSING_OFFER, PACT_OFFER, ARCHIVE_TROVE }

data class DimensionObjectiveWeight(val objective: DimensionObjective, val weight: Float) {
    init { require(weight.isFinite() && weight > 0f) { "Dimension objective weight must be positive" } }
}

/** What realspace-only systems may run in a layer (other lanes read these, never the layer itself). */
data class LayerFeatures(
    /** Window offers, caches, eggs, sigils and event signals. */
    val sites: Boolean = true,
    /** Structure placement. */
    val structures: Boolean = true,
    /** Ambient world events. */
    val worldEvents: Boolean = true,
    val totem: Boolean = true,
    /** Scheduled Elites. */
    val elites: Boolean = true,
    /** Scheduled Heralds. */
    val heralds: Boolean = true,
) {
    companion object {
        val REALSPACE: LayerFeatures = LayerFeatures()
        val DIMENSION: LayerFeatures = LayerFeatures(
            sites = false, structures = false, worldEvents = false, totem = false, elites = false, heralds = false,
        )
    }
}

data class DimensionPhysics(
    val dragMultiplier: Float = 1f,
    val dragFloor: Float = 0f,
    val haloRepairShare: Float = 0f,
    val haloOverdrive: Float = 0f,
    val ambientHalos: Int = 0,
    val scorchSharePerSecond: Float = 0f,
    val scorchGraceSeconds: Float = 0f,
    val scorchTickSeconds: Float = 0.5f,
    val desyncMinimumScale: Float = 1f,
    val desyncMaximumScale: Float = 1f,
    val desyncBossFloor: Float = 1f,
    val matterMultiplier: Float = 1f,
)

data class DimensionReward(
    val kind: DimensionRewardKind,
    /** Cards offered (0..3); a Pact's decline card is extra. */
    val cardsOnSuccess: Int = 0,
    val cardsOnFailure: Int = 0,
    val matterOnSuccess: Float = 0f,
    val matterOnFailure: Float = 0f,
    /** Matter the Pact "Refuse" card salvages. */
    val declineMatter: Int = 0,
)

data class DimensionDefinition(
    val layer: WorldLayer,
    /** English content key: "Realspace", "Zenith", "Nadir", "Archive". */
    val name: String,
    /** Site name of a rift into this layer ("Zenith rift"; BASE: "Exit rift"). */
    val riftName: String,
    /** Codex line. */
    val description: String,
    /** BASE 0. */
    val visitSeconds: Float,
    /** Empty on BASE. */
    val objectives: ImmutableList<DimensionObjectiveWeight>,
    val spawnRateMultiplier: Float = 1f,
    val ordinaryCapMultiplier: Float = 1f,
    val physics: DimensionPhysics = DimensionPhysics(),
    val reward: DimensionReward = DimensionReward(DimensionRewardKind.NONE),
    val features: LayerFeatures = LayerFeatures.DIMENSION,
    /** The share of its spawn integrity this layer's objective boss arrives with. */
    val bossIntegrityShare: Float = 1f,
    /**
     * The share of its shot and contact damage this layer's objective boss deals. The ember bursts it queues are
     * not scaled: they detonate at full damage, as the share was tuned.
     */
    val bossDamageShare: Float = 1f,
) {
    init {
        require(name.isNotBlank() && riftName.isNotBlank() && description.isNotBlank()) { "Dimension texts must not be blank" }
        require(bossIntegrityShare in 0.1f..1f) { "Dimension boss integrity share must be in 0.1..1" }
        require(bossDamageShare in 0.25f..1f) { "Dimension boss damage share must be in 0.25..1" }
        // The hostile-scale range of run modifiers.
        require(spawnRateMultiplier in 0.5f..1.5f && ordinaryCapMultiplier in 0.5f..1.5f) {
            "Dimension spawn and cap multipliers must be in 0.5..1.5"
        }
    }
}

data class DimensionObjectiveTuning(
    val ascentRings: Int = 10,
    val ascentFirstDistance: Float = 700f,
    val ascentMinimumSpacing: Float = 650f,
    val ascentMaximumSpacing: Float = 900f,
    val ascentMinimumTurnDegrees: Float = 20f,
    val ascentMaximumTurnDegrees: Float = 70f,
    /**
     * Once a course ring counts, the next must be passed within this multiple of the cruise time between the two
     * (their distance over the pace), or the course breaks.
     */
    val ascentSplitFactor: Float = 4.25f,
    val haloRadius: Float = 70f,
    val harvestEmbers: Int = 20,
    /** A thrown ember waits this long: the Harvest is a chase to each one, not a sweep behind the kills. */
    val emberLifeSeconds: Float = 1.25f,
    val emberRadius: Float = 70f,
    /** A burning kill throws its ember this far (from the minimum to the maximum, by a stateless hash). */
    val emberThrowMinimum: Float = 140f,
    val emberThrowMaximum: Float = 320f,
    val bossDistance: Float = 900f,
    val bossDelaySeconds: Float = 2f,
    val antecedentDelaySeconds: Float = 4f,
) {
    init {
        require(ascentRings in 2..16) { "Ascent rings must be in 2..16" }
        require(harvestEmbers in 1..ContentBounds.MAX_EMBERS) { "Harvest embers must be in 1..${ContentBounds.MAX_EMBERS}" }
        listOf(ascentFirstDistance, ascentMinimumSpacing, ascentMaximumSpacing, haloRadius, emberRadius, bossDistance, emberLifeSeconds).forEach {
            require(it.isFinite() && it > 0f) { "Dimension objective distances, radii and lifetimes must be positive" }
        }
        require(ascentMinimumSpacing <= ascentMaximumSpacing && ascentMinimumTurnDegrees <= ascentMaximumTurnDegrees) {
            "Ascent spacing and turn bounds must be ordered"
        }
        require(ascentMinimumTurnDegrees >= 0f && ascentMaximumTurnDegrees <= 180f) { "Ascent turns must be in 0..180 degrees" }
        require(ascentSplitFactor in 1f..10f) { "Ascent split factor must be in 1..10" }
        require(emberThrowMinimum in 0f..600f && emberThrowMaximum in emberThrowMinimum..600f) {
            "Ember throws must be ordered within 0..600"
        }
        require(bossDelaySeconds.isFinite() && bossDelaySeconds >= 0f && antecedentDelaySeconds.isFinite() && antecedentDelaySeconds >= 0f) {
            "Boss delays must be non-negative"
        }
    }
}

data class DimensionPolicy(
    /** False: no rifts at all (the harness "without dimensions" arm). */
    val enabled: Boolean = true,
    val portalWindowChance: Float = 0.30f,
    val firstPortalWindow: Int = 1,
    val maxPortalOffersPerRun: Int = 3,
    val exitMarginSeconds: Float = 60f,
    val minimumVisitSeconds: Float = 30f,
    val arrivalClearRadius: Float = 900f,
    val portalRadius: Float = 65f,
    val exitPortalDistance: Float = 520f,
    val glitchPortalDistance: Float = 700f,
    val glitchPortalLifetimeSeconds: Float = 30f,
    val paceReach: Float = 0.40f,
    val paceCeiling: Float = 900f,
    val objectives: DimensionObjectiveTuning = DimensionObjectiveTuning(),
    val definitions: ImmutableList<DimensionDefinition> = defaultDimensionDefinitions(),
) {
    init {
        require(WorldLayer.entries.size <= 8) { "World layers fit three id bits" }
        require(definitions.size == WorldLayer.entries.size && definitions.withIndex().all { (index, d) -> d.layer.ordinal == index }) {
            "Dimension definitions must follow the WorldLayer order"
        }
        require(portalWindowChance in 0f..1f) { "Portal window chance must be in 0..1" }
        require(firstPortalWindow in 0..5) { "First portal window must be in 0..5" }
        require(maxPortalOffersPerRun in 0..ContentBounds.MAX_PORTAL_OFFERS) { "Portal offers must be in 0..${ContentBounds.MAX_PORTAL_OFFERS}" }
        require(exitMarginSeconds in 30f..120f) { "Exit margin must be in 30..120 s" }
        require(arrivalClearRadius in 300f..1_500f) { "Arrival clear radius must be in 300..1500" }
        require(glitchPortalLifetimeSeconds in 10f..40f) { "Glitch portal lifetime must be in 10..40 s" }
        require(paceReach in 0.2f..0.8f) { "Pace reach must be in 0.2..0.8" }
        require(paceCeiling in 300f..2_000f) { "Pace ceiling must be in 300..2000" }
        require(listOf(portalRadius, exitPortalDistance, glitchPortalDistance).all { it.isFinite() && it > 0f }) {
            "Portal distances must be positive"
        }
        require(minimumVisitSeconds >= 10f) { "A visit lasts at least 10 s" }
        val base = definitions[WorldLayer.BASE.ordinal]
        require(
            base.visitSeconds == 0f && base.objectives.isEmpty() && base.features == LayerFeatures.REALSPACE &&
                base.physics == DimensionPhysics() && base.reward == DimensionReward(DimensionRewardKind.NONE) &&
                base.spawnRateMultiplier == 1f && base.ordinaryCapMultiplier == 1f,
        ) { "Realspace has no visit, objectives, physics, director change or reward" }
        definitions.filter { it.layer != WorldLayer.BASE }.forEach { definition ->
            require(definition.visitSeconds in minimumVisitSeconds..120f) { "A dimension visit must be in $minimumVisitSeconds..120 s" }
            require(definition.objectives.isNotEmpty() && definition.objectives.all { it.objective.layer == definition.layer }) {
                "A dimension rolls only its own objectives"
            }
            val physics = definition.physics
            require(physics.dragMultiplier > 0.3f && physics.dragMultiplier <= 1f) { "Dimension drag multiplier must be in (0.3, 1]" }
            require(physics.dragFloor.isFinite() && physics.dragFloor >= 0f) { "Dimension drag floor must be non-negative" }
            require(physics.haloRepairShare in 0f..0.2f && physics.haloOverdrive in 0f..50f) { "Halo rewards are outside their bounds" }
            require(physics.ambientHalos in 0..(ContentBounds.MAX_HALO_RINGS - 2)) { "Ambient halos must leave room for the course" }
            require(
                physics.scorchSharePerSecond in 0f..0.1f && physics.scorchGraceSeconds.isFinite() && physics.scorchGraceSeconds >= 0f &&
                    physics.scorchTickSeconds.isFinite() && physics.scorchTickSeconds > 0f,
            ) {
                "Scorch must be in 0..0.1 per second with a positive tick"
            }
            require(
                physics.desyncMinimumScale >= 0.1f && physics.desyncMinimumScale <= 1f && physics.desyncMaximumScale >= 1f &&
                    physics.desyncMaximumScale <= 2f && physics.desyncBossFloor in physics.desyncMinimumScale..1f,
            ) { "Desync scales must satisfy 0.1 <= min <= 1 <= max <= 2 and min <= boss floor <= 1" }
            require(physics.matterMultiplier in 1f..3f) { "Dimension Matter multiplier must be in 1..3" }
            val reward = definition.reward
            require(reward.cardsOnSuccess in 0..3 && reward.cardsOnFailure in 0..3) { "Dimension reward cards must be in 0..3" }
            require(reward.declineMatter == 0 || reward.kind == DimensionRewardKind.PACT_OFFER) { "Only a Pact offer has a decline card" }
            require(
                reward.declineMatter >= 0 && reward.matterOnSuccess.isFinite() && reward.matterOnSuccess >= 0f &&
                    reward.matterOnFailure.isFinite() && reward.matterOnFailure >= 0f,
            ) {
                "Dimension reward Matter must be non-negative"
            }
        }
    }

    fun definition(layer: WorldLayer): DimensionDefinition = definitions[layer.ordinal]

    /** The longest visit a window rift can open: window rifts lead only into the Zenith or the Nadir. */
    val longestWindowVisitSeconds: Float
        get() = maxOf(definition(WorldLayer.ZENITH).visitSeconds, definition(WorldLayer.NADIR).visitSeconds)

    /**
     * True when the POI window [windowIndex], scheduled at [scheduledAt] and offered for [offerLifetime] seconds, may
     * host a rift: it is not before [firstPortalWindow], and even a visit entered as the offer closes ends
     * [exitMarginSeconds] before the boss at [bossAtSeconds].
     */
    fun windowMayHostRift(windowIndex: Int, scheduledAt: Float, offerLifetime: Float, bossAtSeconds: Float): Boolean =
        windowIndex >= firstPortalWindow &&
            scheduledAt + offerLifetime <= bossAtSeconds - longestWindowVisitSeconds - exitMarginSeconds

    /** The latest run time the glitch rift into the Archive may appear so its visit still ends [exitMarginSeconds] before the boss. */
    fun latestGlitchRiftAt(bossAtSeconds: Float): Float =
        bossAtSeconds - exitMarginSeconds - definition(WorldLayer.ARCHIVE).visitSeconds - glitchPortalLifetimeSeconds
}

/** The shipped layers in [WorldLayer] order. */
fun defaultDimensionDefinitions(): ImmutableList<DimensionDefinition> = listOf(
    DimensionDefinition(
        WorldLayer.BASE, "Realspace", "Exit rift", "Realspace", visitSeconds = 0f, objectives = immutableListOf(),
        features = LayerFeatures.REALSPACE,
    ),
    DimensionDefinition(
        WorldLayer.ZENITH, "Zenith", "Zenith rift",
        "A layer of light above realspace. Grace lightens the Core and halo gates mend it. Blessings wait for those who finish its trial.",
        visitSeconds = 75f,
        objectives = immutableListOf(
            DimensionObjectiveWeight(DimensionObjective.ASCENT, 1f), DimensionObjectiveWeight(DimensionObjective.JUDGEMENT, 1f),
        ),
        spawnRateMultiplier = 0.9f, ordinaryCapMultiplier = 0.9f,
        physics = DimensionPhysics(dragMultiplier = 0.65f, dragFloor = 0.035f, haloRepairShare = 0.05f, haloOverdrive = 15f, ambientHalos = 6),
        reward = DimensionReward(DimensionRewardKind.BLESSING_OFFER, cardsOnSuccess = 3, cardsOnFailure = 2),
        // The Throne of a Judgement: a fresh run's visit at 165 s fells it in about four of five fights, a strong
        // tier-10 build's at 285 s in about two of three (the share shortens the fight in capped hits too).
        bossIntegrityShare = 0.16f,
    ),
    DimensionDefinition(
        WorldLayer.NADIR, "Nadir", "Nadir rift",
        "A layer of heat beneath realspace. Slow flight scorches, and Matter burns twice as bright. Pacts are offered at a price.",
        // A shorter stay than the Zenith's: a fresh run's Pyre fight under the Herald's volleys turned deadly late in a
        // 75 s visit, while a Harvest is done well inside 60 s.
        visitSeconds = 60f,
        objectives = immutableListOf(
            DimensionObjectiveWeight(DimensionObjective.HARVEST, 1f), DimensionObjectiveWeight(DimensionObjective.PYRE, 1f),
        ),
        spawnRateMultiplier = 1.2f, ordinaryCapMultiplier = 1.15f,
        physics = DimensionPhysics(scorchSharePerSecond = 0.035f, scorchGraceSeconds = 0.75f, scorchTickSeconds = 0.5f, matterMultiplier = 2f),
        reward = DimensionReward(DimensionRewardKind.PACT_OFFER, cardsOnSuccess = 3, cardsOnFailure = 2, declineMatter = 10),
        // The Pyre Herald of a Pyre, fought at the pace the scorch asks for: a fresh run's visit at 165 s fells it in
        // over half of the fights.
        bossIntegrityShare = 0.12f,
        // Its spiral volleys late in a fresh run's visit were the Nadir's trap (the deaths inside a Pyre outweighed what
        // its Pacts cost afterwards).
        bossDamageShare = 0.6f,
    ),
    DimensionDefinition(
        WorldLayer.ARCHIVE, "Archive", "Glitch rift",
        "The Architect's memory. Time moves only when you move. Something older waits inside.",
        visitSeconds = 100f,
        objectives = immutableListOf(DimensionObjectiveWeight(DimensionObjective.ANTECEDENT, 1f)),
        spawnRateMultiplier = 0.85f, ordinaryCapMultiplier = 0.8f,
        physics = DimensionPhysics(desyncMinimumScale = 0.25f, desyncMaximumScale = 1.25f, desyncBossFloor = 0.5f),
        reward = DimensionReward(DimensionRewardKind.ARCHIVE_TROVE, cardsOnSuccess = 3, matterOnSuccess = 120f, matterOnFailure = 60f),
    ),
).toImmutableList()

/** Every message string the dimension system writes; ContentLocalizationTest iterates [all]. */
object DimensionNotices {
    const val COMPLETE: String = "Complete"
    const val EXIT_OPEN: String = "EXIT RIFT OPEN"
    const val PULLED_BACK: String = "PULLED BACK TO REALSPACE"
    const val REALSPACE: String = "REALSPACE"
    const val ARCHIVE_STIRS: String = "THE ARCHIVE STIRS"
    const val SCORCHED: String = "SCORCHED"
    const val RIFT_DETECTED: String = "RIFT DETECTED // CHOOSE A COURSE"
    const val COURSE_BROKEN: String = "Course broken"

    /** Layer names equal the definitions' `name` keys: Realspace, Zenith, Nadir, Archive. */
    fun layerName(layer: WorldLayer): String = when (layer) {
        WorldLayer.BASE -> "Realspace"
        WorldLayer.ZENITH -> "Zenith"
        WorldLayer.NADIR -> "Nadir"
        WorldLayer.ARCHIVE -> "Archive"
    }

    fun entry(layer: WorldLayer, objective: DimensionObjective): String = "${layerName(layer)} // ${objective.label}".uppercase()

    fun completed(objective: DimensionObjective): String = "${objective.label} // $COMPLETE".uppercase()

    /** The Ascent's course broke: its next ring was not reached in time. */
    fun broken(objective: DimensionObjective): String = "${objective.label} // $COURSE_BROKEN".uppercase()

    /** Every entry banner of a layer and its own objectives, every completion, the broken course, and the constants above. */
    val all: List<String> = buildList {
        DimensionObjective.entries.forEach { add(entry(it.layer, it)) }
        DimensionObjective.entries.forEach { add(completed(it)) }
        add(broken(DimensionObjective.ASCENT))
        addAll(listOf(COMPLETE, EXIT_OPEN, PULLED_BACK, REALSPACE, ARCHIVE_STIRS, SCORCHED, RIFT_DETECTED))
    }
}
