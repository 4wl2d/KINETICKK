// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

/** Dipole polarity: [ATTRACT] pulls nearby enemies toward the Core, [REPEL] throws them away. */
internal enum class Polarity { ATTRACT, REPEL }

/**
 * Owned character state; all ability charge derives from accepted physical movement and combat.
 *
 * A reduction fork copies the scalars (a few dozen fields) and forks the bounded buffers, allocated
 * once per run and shared with the committed state until the candidate first writes them. The echo
 * ghost's path is paged: a fixed step writes one sample and copies at most that sample's small page
 * (plus, once per reduction, the page table), never the whole ring. The lattice nodes, the drones and
 * the void scars are small arrays copied at most once per reduction; later steps write them in place.
 */
internal class CharacterRuntime private constructor(
    /**
     * Ring buffer of Core positions, one [GHOST_STRIDE] record (x, y, teleport flag) per fixed step;
     * the ghost replays it [GHOST_DELAY_STEPS] behind.
     */
    val ghostPath: CopyOnWritePagedFloatArray,
    /** Up to [MAX_LATTICE_NODES] (x, y) pairs; [latticeCount] are live. */
    val latticeNodes: CopyOnWriteFloatArray,
    /** Up to [MAX_DRONES] records of [DRONE_STRIDE] floats; [droneCount] are live. */
    val drones: CopyOnWriteFloatArray,
    /** Up to [MAX_SCARS] records of [SCAR_STRIDE] floats; [scarCount] are live, oldest first. */
    val scars: CopyOnWriteFloatArray,
) {
    constructor() : this(
        ghostPath = CopyOnWritePagedFloatArray(GHOST_SAMPLES * GHOST_STRIDE, GHOST_PAGE_SAMPLES * GHOST_STRIDE),
        latticeNodes = CopyOnWriteFloatArray(FloatArray(MAX_LATTICE_NODES * 2)),
        drones = CopyOnWriteFloatArray(FloatArray(MAX_DRONES * DRONE_STRIDE)),
        scars = CopyOnWriteFloatArray(FloatArray(MAX_SCARS * SCAR_STRIDE)),
    )

    /** Mechanic readiness 0..1 for the HUD (Orbit charge, parry readiness, lattice fill and so on). */
    var charge: Float = 0f
    var previousSpeed: Float = 0f
    var previousHeading: Float = 0f
    var turnArc: Float = 0f
    var turnDistance: Float = 0f
    var wasBraking: Boolean = false
    var dashSequence: Int = 0
    var hasDashOrigin: Boolean = false
    var dashOriginX: Float = 0f
    var dashOriginY: Float = 0f
    var dashRecorded: Boolean = false
    /** Puncture (Shard): when the next mark may be opened. */
    var punctureReadyAt: Float = 0f

    /** Halo (Ring): current radius, when the next Halo Burst may fire and the burst flash (1 fading to 0). */
    var haloRadius: Float = HALO_MIN_RADIUS
    var haloBurstReadyAt: Float = 0f
    var haloBurstFlash: Float = 0f

    /** Facets (Prism): intact facets, seconds toward the next one, the regeneration pause and the impact cut gate. */
    var facets: Int = MAX_FACETS
    var facetTimer: Float = 0f
    var facetPauseUntil: Float = 0f
    var facetCutReadyAt: Float = 0f

    /** Parry (Diamond): open window, cooldown, Edge stacks and their expiry. */
    var parryWindow: Float = 0f
    var parryCooldown: Float = 0f
    var edge: Int = 0
    var edgeUntil: Float = 0f
    /** Steps the last parry stays observable; see `parriedThisStep`. */
    var parryFlagSteps: Int = 0

    /** Lattice (Tesseract): live node count and their remaining lifetime. */
    var latticeCount: Int = 0
    var latticeTime: Float = 0f

    /** Nova (Star): Stellar Mass stacks, the Supernova cooldown and its flash. */
    var stellarMass: Int = 0
    var supernovaReadyAt: Float = 0f
    var novaFlash: Float = 0f

    /** Echo (Spiral): newest ring-buffer slot, samples written (capped) and the projectile absorption gate. */
    var ghostHead: Int = -1
    var ghostSamples: Int = 0
    var ghostAbsorbReadyAt: Float = 0f

    /** Polarity (Dipole): current polarity, when it may flip again and the flip flash. */
    var polarity: Polarity = Polarity.ATTRACT
    var polarityFlipReadyAt: Float = 0f
    var polarityFlash: Float = 0f

    /** Pulse (Pentagon): streak stacks, the beat that last raised it and whether the current Dash left on a beat. */
    var beatStreak: Int = 0
    var streakBeat: Int = Int.MIN_VALUE
    var dashOnBeat: Boolean = false
    /** The beat whose on-beat impact last released a pulse burst (one per beat). */
    var pulseBeat: Int = Int.MIN_VALUE

    /** Eclipse (Crescent): Blink charges, seconds toward the next charge and the last Blink segment. */
    var blinkCharges: Int = MAX_BLINK_CHARGES
    var blinkRecharge: Float = 0f
    /** Steps the last Blink (the Crescent's or Blink Drive's) stays observable; see `blinkThisStep`. */
    var blinkFlagSteps: Int = 0
    var blinkFromX: Float = 0f
    var blinkFromY: Float = 0f
    var blinkToX: Float = 0f
    var blinkToY: Float = 0f
    var scarCount: Int = 0

    /** Hive (Hexagon): live drones, Data pickups counted toward the guaranteed drone and the kill-heal gate. */
    var droneCount: Int = 0
    var droneRolls: Int = 0
    var droneHealReadyAt: Float = 0f

    /** Ability-only output this run: damage the mechanic dealt and damage it prevented (1 integrity = 1 damage). */
    var abilityDamage: Double = 0.0
    var damagePrevented: Double = 0.0

    /** An isolated candidate: scalars copied, buffers shared until the first changed write. */
    fun fork(): CharacterRuntime = CharacterRuntime(
        ghostPath = ghostPath.fork(),
        latticeNodes = latticeNodes.fork(),
        drones = drones.fork(),
        scars = scars.fork(),
    ).also { copy ->
        copy.charge = charge
        copy.previousSpeed = previousSpeed
        copy.previousHeading = previousHeading
        copy.turnArc = turnArc
        copy.turnDistance = turnDistance
        copy.wasBraking = wasBraking
        copy.dashSequence = dashSequence
        copy.hasDashOrigin = hasDashOrigin
        copy.dashOriginX = dashOriginX
        copy.dashOriginY = dashOriginY
        copy.dashRecorded = dashRecorded
        copy.punctureReadyAt = punctureReadyAt
        copy.haloRadius = haloRadius
        copy.haloBurstReadyAt = haloBurstReadyAt
        copy.haloBurstFlash = haloBurstFlash
        copy.facets = facets
        copy.facetTimer = facetTimer
        copy.facetPauseUntil = facetPauseUntil
        copy.facetCutReadyAt = facetCutReadyAt
        copy.parryWindow = parryWindow
        copy.parryCooldown = parryCooldown
        copy.edge = edge
        copy.edgeUntil = edgeUntil
        copy.parryFlagSteps = parryFlagSteps
        copy.latticeCount = latticeCount
        copy.latticeTime = latticeTime
        copy.stellarMass = stellarMass
        copy.supernovaReadyAt = supernovaReadyAt
        copy.novaFlash = novaFlash
        copy.ghostHead = ghostHead
        copy.ghostSamples = ghostSamples
        copy.ghostAbsorbReadyAt = ghostAbsorbReadyAt
        copy.polarity = polarity
        copy.polarityFlipReadyAt = polarityFlipReadyAt
        copy.polarityFlash = polarityFlash
        copy.beatStreak = beatStreak
        copy.streakBeat = streakBeat
        copy.dashOnBeat = dashOnBeat
        copy.pulseBeat = pulseBeat
        copy.blinkCharges = blinkCharges
        copy.blinkRecharge = blinkRecharge
        copy.blinkFlagSteps = blinkFlagSteps
        copy.blinkFromX = blinkFromX
        copy.blinkFromY = blinkFromY
        copy.blinkToX = blinkToX
        copy.blinkToY = blinkToY
        copy.scarCount = scarCount
        copy.droneCount = droneCount
        copy.droneRolls = droneRolls
        copy.droneHealReadyAt = droneHealReadyAt
        copy.abilityDamage = abilityDamage
        copy.damagePrevented = damagePrevented
    }

    /** Every retained fact in a stable order (state witnesses and diagnostics). */
    override fun toString(): String = buildString {
        append("CharacterRuntime(")
        append(charge).append(',').append(previousSpeed).append(',').append(previousHeading).append(',')
        append(turnArc).append(',').append(turnDistance).append(',').append(wasBraking).append(',')
        append(dashSequence).append(',').append(hasDashOrigin).append(',').append(dashOriginX).append(',')
        append(dashOriginY).append(',').append(dashRecorded).append(',').append(punctureReadyAt).append(',')
        append(haloRadius).append(',').append(haloBurstReadyAt).append(',').append(haloBurstFlash).append(',')
        append(facets).append(',').append(facetTimer).append(',').append(facetPauseUntil).append(',')
        append(facetCutReadyAt).append(',').append(parryWindow).append(',').append(parryCooldown).append(',')
        append(edge).append(',').append(edgeUntil).append(',').append(parryFlagSteps).append(',')
        append(latticeCount).append(',').append(latticeTime).append(',').append(stellarMass).append(',')
        append(supernovaReadyAt).append(',').append(novaFlash).append(',').append(ghostHead).append(',')
        append(ghostSamples).append(',').append(ghostAbsorbReadyAt).append(',').append(polarity).append(',')
        append(polarityFlipReadyAt).append(',').append(polarityFlash).append(',').append(beatStreak).append(',')
        append(streakBeat).append(',').append(dashOnBeat).append(',').append(pulseBeat).append(',')
        append(blinkCharges).append(',').append(blinkRecharge).append(',').append(blinkFlagSteps).append(',')
        append(blinkFromX).append(',').append(blinkFromY).append(',').append(blinkToX).append(',')
        append(blinkToY).append(',').append(scarCount).append(',').append(droneCount).append(',')
        append(droneRolls).append(',').append(droneHealReadyAt).append(',').append(abilityDamage).append(',')
        append(damagePrevented)
        append(";ghost=").append(ghostPath.toList())
        append(";lattice=").append(latticeNodes.toList())
        append(";drones=").append(drones.toList())
        append(";scars=").append(scars.toList())
        append(')')
    }

    /** Lattice node [index] (0 until [latticeCount]). */
    fun latticeX(index: Int): Float = latticeNodes[index * 2]
    fun latticeY(index: Int): Float = latticeNodes[index * 2 + 1]

    fun droneValue(index: Int, field: Int): Float = drones[index * DRONE_STRIDE + field]
    fun scarValue(index: Int, field: Int): Float = scars[index * SCAR_STRIDE + field]

    /** Core position [stepsAgo] fixed steps ago, or the oldest recorded sample when the path is shorter. */
    fun ghostSampleX(stepsAgo: Int): Float = ghostPath[ghostSlot(stepsAgo) * GHOST_STRIDE + GHOST_X]
    fun ghostSampleY(stepsAgo: Int): Float = ghostPath[ghostSlot(stepsAgo) * GHOST_STRIDE + GHOST_Y]

    /** True when the Core reached the sample [stepsAgo] by a teleport rather than by moving. */
    fun ghostSampleTeleported(stepsAgo: Int): Boolean =
        ghostPath[ghostSlot(stepsAgo) * GHOST_STRIDE + GHOST_TELEPORT].toRawBits() != 0

    /** Appends the newest sample, overwriting the oldest once the ring is full. */
    fun recordGhostSample(x: Float, y: Float, teleported: Boolean) {
        val head = (ghostHead + 1) % GHOST_SAMPLES
        ghostHead = head
        val base = head * GHOST_STRIDE
        ghostPath[base + GHOST_X] = x
        ghostPath[base + GHOST_Y] = y
        ghostPath[base + GHOST_TELEPORT] = if (teleported) 1f else 0f
        if (ghostSamples < GHOST_SAMPLES) ghostSamples++
    }

    private fun ghostSlot(stepsAgo: Int): Int {
        val back = stepsAgo.coerceIn(0, (ghostSamples - 1).coerceAtLeast(0))
        return ((ghostHead - back) % GHOST_SAMPLES + GHOST_SAMPLES) % GHOST_SAMPLES
    }

    companion object {
        const val MAX_FACETS: Int = 4
        const val MAX_EDGE: Int = 5
        const val MAX_LATTICE_NODES: Int = 4
        const val MAX_STELLAR_MASS: Int = 24
        const val MAX_BLINK_CHARGES: Int = 2
        const val MAX_DRONES: Int = 4
        const val MAX_SCARS: Int = 4
        const val MAX_BEAT_STREAK: Int = 5
        const val HALO_MIN_RADIUS: Float = 55f
        /** The ghost runs one second (120 fixed steps) behind; one more sample gives its previous position. */
        const val GHOST_DELAY_STEPS: Int = 120
        const val GHOST_SAMPLES: Int = GHOST_DELAY_STEPS + 2

        /** Ghost record: position and a teleport flag (1 when the Core arrived there by a teleport). */
        const val GHOST_X: Int = 0
        const val GHOST_Y: Int = 1
        const val GHOST_TELEPORT: Int = 2
        const val GHOST_STRIDE: Int = 3
        /** Samples per copy-on-write page of the ghost ring: a step copies at most 24 floats. */
        const val GHOST_PAGE_SAMPLES: Int = 8

        /** Drone record: position, previous position, remaining life, next dart time, dart time left, dart target. */
        const val DRONE_X: Int = 0
        const val DRONE_Y: Int = 1
        const val DRONE_PREVIOUS_X: Int = 2
        const val DRONE_PREVIOUS_Y: Int = 3
        const val DRONE_LIFE: Int = 4
        const val DRONE_READY_AT: Int = 5
        const val DRONE_DART: Int = 6
        const val DRONE_TARGET_X: Int = 7
        const val DRONE_TARGET_Y: Int = 8
        const val DRONE_STRIDE: Int = 9

        /** Scar record: segment endpoints, detonation time and seconds since it detonated (negative before). */
        const val SCAR_X0: Int = 0
        const val SCAR_Y0: Int = 1
        const val SCAR_X1: Int = 2
        const val SCAR_Y1: Int = 3
        const val SCAR_DETONATE_AT: Int = 4
        const val SCAR_AGE: Int = 5
        const val SCAR_STRIDE: Int = 6
    }
}
