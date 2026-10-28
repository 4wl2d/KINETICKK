// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * World obstacle field: generation, Core collisions, enemy slams and the minimap. Distances are dp
 * world units and speeds dp per second.
 *
 * The collision bands are expressed as cursor reach, the same normalized center-to-edge measure as
 * [FatigueTuning.edgeStrainStart]: a collision's impact speed (normal speed plus [scrapeShare] of the
 * sliding speed) is compared with the straight steady speed that holding the cursor at that reach along
 * the viewport's long axis sustains. Steady speed grows with the viewport, so
 * fixed speeds would make a wide window lethal at a gentle reach and a phone safe at the edge; the long
 * axis keeps the bands the same for every heading.
 */
data class ObstacleTuning(
    /** Side of one generation chunk. */
    val chunkSize: Float = 1_200f,
    /** Chunks per side of the generated window around the Core's chunk (odd). */
    val windowChunks: Int = 5,
    /** Mean obstacles per chunk before Rebirth density; a chunk holds at most [maximumPerChunk]. */
    val meanPerChunk: Float = 2.2f,
    val maximumPerChunk: Int = 5,
    /** Fractured Space multiplies the mean by this. */
    val fracturedDensityMultiplier: Float = 1.4f,
    val asteroidMinimumRadius: Float = 46f,
    val asteroidMaximumRadius: Float = 150f,
    val bumperRadius: Float = 34f,
    /** Share of obstacles that are bumpers; Fractured Space uses [fracturedBumperShare]. */
    val bumperShare: Float = 0.18f,
    val fracturedBumperShare: Float = 0.08f,
    /** Share of asteroids that are unstable, only under Fractured Space. */
    val fracturedUnstableShare: Float = 0.15f,
    /** No obstacle reaches inside this distance of the run origin. */
    val safeRadius: Float = 900f,
    /** Least clear space between two obstacles. */
    val minimumGap: Float = 90f,
    /**
     * Cursor reach whose steady speed an impact needs to hurt (tier 3 and above); every hurting hit costs at least
     * [minimumDamageShare].
     */
    val damageStartReach: Float = 0.32f,
    /** Cursor reach whose steady speed makes an impact lethal before Matrix and item bonuses (reach 0.6 hurts). */
    val lethalReach: Float = 0.67f,
    /**
     * A Dash from a cruise at this reach is never lethal: the lethal speed stays at least
     * [dashLethalMargin] above that cruise speed plus the Dash impulse.
     */
    val dashSafeReach: Float = 0.30f,
    val dashLethalMargin: Float = 60f,
    /** At tier 0 collisions start hurting this much faster, shrinking linearly to 0 at [forgivingTiers]. */
    val forgivingStartBonus: Float = 40f,
    /** At tier 0 collision damage is multiplied by this, rising linearly to 1 at [forgivingTiers]. */
    val forgivingDamageScale: Float = 0.7f,
    val forgivingTiers: Int = 3,
    /** Collision damage at the lethal end of the band as a share of maximum integrity. */
    val damageShare: Float = 0.55f,
    /** Shape of the band from [minimumDamageShare] to [damageShare]: 1 is linear. */
    val damageExponent: Float = 1.0f,
    /** Collision damage grows by this per Rebirth tier (it replaces the Rebirth incoming-damage multiplier). */
    val damagePerTier: Float = 0.04f,
    val restitution: Float = 0.6f,
    /** A Core bounce below this normal speed slides along the surface instead of bouncing. */
    val slideSpeed: Float = 120f,
    /**
     * Collision damage and ricochets of one surface land at most once per this many seconds; another surface, or a
     * lethal hit, lands at once.
     */
    val hitCooldownSeconds: Float = 0.25f,
    val bumperMinimumExitSpeed: Float = 700f,
    val bumperMaximumExitSpeed: Float = 1_400f,
    val bumperGain: Float = 1.05f,
    val bumperRicochetCooldownSeconds: Float = 1.5f,
    /**
     * A bumper never throws the Core faster than this share of the lethal collision speed along its new
     * heading, so a throw cannot turn the next asteroid into a lethal hit.
     */
    val bumperLethalShare: Float = 0.9f,
    val unstableBlastRadius: Float = 150f,
    val unstableBlastDamage: Float = 10f,
    /** Enemy contact normal speed that slams it, and the speed its slam damage counts from. */
    val slamSpeed: Float = 260f,
    val slamDamageOffset: Float = 200f,
    val slamDamagePerSpeed: Float = 0.12f,
    /** Enemies start sliding around an obstacle within this multiple of the combined radii. */
    val avoidanceRange: Float = 1.6f,
    /** Enemy-enemy slams resolved per fixed step. */
    val maximumEnemySlamsPerStep: Int = 6,
    /** Seconds an enemy stays "thrown" after the player knocks it back (enemy-enemy slams). */
    val thrownSeconds: Float = 0.6f,
    /** Seconds between two slams of the same enemy. */
    val slamCooldownSeconds: Float = 0.3f,
    /** Smallest minimap range; it widens to [minimapViewportFactor] viewport half-diagonals. */
    val minimapRange: Float = 2_200f,
    val minimapViewportFactor: Float = 1.4f,
    /** Blackout multiplies the minimap range by this. */
    val blackoutMinimapScale: Float = 0.5f,
    /** Share of the sliding (tangential) speed a collision adds to its normal speed: a fast glance hurts. */
    val scrapeShare: Float = 0.3f,
    /**
     * Cursor reach whose straight steady speed (plus the tier-0 start bonus) a bounce's normal speed needs to count as a
     * ricochet: RICOCHET clauses, Ricochet Rig, Gravity Surfer, the Dipole counter and the ambush pace.
     */
    val ricochetReach: Float = 0.45f,
    /** Matrix and item bonuses never lift the lethal speed past the build's straight steady speed at this reach. */
    val lethalCeilingReach: Float = 0.77f,
    /** Damage of a hit at the damage-start speed, as a share of maximum integrity. */
    val minimumDamageShare: Float = 0.06f,
    /** Share of collision damage that passes through shields (Glass Comet still lets all of it through). */
    val shieldBleed: Float = 0.4f,
    /** A hurting hit keeps 1 − scrapeFriction × severity of its sliding speed; severity is 1 at the lethal speed. */
    val scrapeFriction: Float = 0.35f,
    /** The obstacle kinds: per-layer mixes and every new kind's mechanics. */
    val kinds: ObstacleKindTuning = ObstacleKindTuning(),
) {
    init {
        require(chunkSize.isFinite() && chunkSize >= 400f) { "Obstacle chunks must be at least 400 u" }
        require(windowChunks in 1..9 && windowChunks % 2 == 1) { "The obstacle window must be an odd chunk count" }
        require(maximumPerChunk in 0..8)
        require(windowChunks * windowChunks * maximumPerChunk <= ContentBounds.MAX_OBSTACLES) {
            "The obstacle window must fit ${ContentBounds.MAX_OBSTACLES} obstacles"
        }
        require(meanPerChunk.isFinite() && meanPerChunk in 0f..maximumPerChunk.toFloat())
        require(fracturedDensityMultiplier.isFinite() && fracturedDensityMultiplier >= 1f)
        require(asteroidMinimumRadius.isFinite() && asteroidMinimumRadius > 0f)
        require(asteroidMaximumRadius.isFinite() && asteroidMaximumRadius >= asteroidMinimumRadius)
        require(bumperRadius.isFinite() && bumperRadius > 0f)
        require(asteroidMaximumRadius * 2f + minimumGap * 2f < chunkSize) { "An asteroid must fit inside a chunk" }
        require(bumperShare in 0f..1f && fracturedBumperShare in 0f..1f && fracturedUnstableShare in 0f..1f)
        require(safeRadius.isFinite() && safeRadius >= 0f)
        require(minimumGap.isFinite() && minimumGap >= 0f)
        require(damageStartReach > 0f && lethalReach > damageStartReach && lethalReach < 1f) {
            "Collision reaches must satisfy 0 < damage start < lethal < 1"
        }
        require(dashSafeReach > 0f && dashSafeReach <= damageStartReach) { "A safe Dash cruise must stay below the damage start" }
        require(dashLethalMargin.isFinite() && dashLethalMargin >= 0f)
        require(forgivingStartBonus.isFinite() && forgivingStartBonus >= 0f)
        require(forgivingDamageScale in 0f..1f && forgivingTiers >= 1)
        require(damageShare.isFinite() && damageShare > 0f && damageExponent.isFinite() && damageExponent > 0f)
        require(damagePerTier.isFinite() && damagePerTier >= 0f)
        require(restitution in 0f..1f) { "Restitution must stay within 0..1" }
        require(slideSpeed.isFinite() && slideSpeed >= 0f)
        require(hitCooldownSeconds.isFinite() && hitCooldownSeconds >= 0f)
        require(bumperMinimumExitSpeed > 0f && bumperMaximumExitSpeed >= bumperMinimumExitSpeed && bumperGain >= 1f)
        require(bumperRicochetCooldownSeconds.isFinite() && bumperRicochetCooldownSeconds >= 0f)
        require(bumperLethalShare > 0f && bumperLethalShare < 1f) { "A bumper throw must stay below the lethal speed" }
        require(unstableBlastRadius.isFinite() && unstableBlastRadius > 0f && unstableBlastDamage.isFinite() && unstableBlastDamage >= 0f)
        require(slamSpeed > slamDamageOffset && slamDamageOffset >= 0f && slamDamagePerSpeed > 0f)
        require(avoidanceRange.isFinite() && avoidanceRange >= 1f)
        require(maximumEnemySlamsPerStep in 0..32)
        require(thrownSeconds.isFinite() && thrownSeconds >= 0f && slamCooldownSeconds.isFinite() && slamCooldownSeconds >= 0f)
        require(minimapRange.isFinite() && minimapRange > 0f && minimapViewportFactor.isFinite() && minimapViewportFactor > 0f)
        require(blackoutMinimapScale.isFinite() && blackoutMinimapScale > 0f && blackoutMinimapScale <= 1f)
        require(scrapeShare in 0f..1f) { "Scrape share must be in 0..1" }
        require(ricochetReach >= damageStartReach && ricochetReach < 1f) { "A ricochet is always a hurting hit" }
        require(lethalCeilingReach > lethalReach && lethalCeilingReach < 1f) { "The lethal ceiling must lie past the lethal reach" }
        require(minimumDamageShare >= 0f && minimumDamageShare < damageShare) { "The damage floor must stay below the lethal share" }
        require(shieldBleed in 0f..1f && scrapeFriction in 0f..1f) { "Shield bleed and scrape friction must be in 0..1" }
        require(
            kinds.gateMaximumRadius <= asteroidMaximumRadius && kinds.mineClusterMaximumRadius <= asteroidMaximumRadius &&
                kinds.ventMaximumRadius <= asteroidMaximumRadius && kinds.wellCoreRadius <= asteroidMaximumRadius,
        ) { "Every obstacle kind body must fit the largest asteroid" }
        require(kinds.wellCoreRadius >= bumperRadius) { "A gravity knot is at least bumper sized" }
        require(kinds.crystalMaximumCandidate >= asteroidMinimumRadius) { "Crystals must be able to replace the smallest asteroid" }
    }
}

/**
 * Data XP pickups. A pickup entering [magnetRadius] latches onto the Core and homes at
 * `max(vacuumSpeed, coreSpeed × vacuumCoreSpeedFactor)` until collected within [collectionRadius].
 */
data class PickupTuning(
    /** Base magnet radius before items and the Matrix. */
    val magnetRadius: Float = 320f,
    val collectionRadius: Float = 56f,
    val vacuumSpeed: Float = 950f,
    val vacuumCoreSpeedFactor: Float = 1.3f,
    /** Floor of the pickup radius after item and Matrix stat changes. */
    val minimumMagnetRadius: Float = 120f,
) {
    init {
        require(magnetRadius.isFinite() && magnetRadius > 0f)
        require(collectionRadius.isFinite() && collectionRadius > 0f && collectionRadius < magnetRadius)
        require(vacuumSpeed.isFinite() && vacuumSpeed > 0f)
        require(vacuumCoreSpeedFactor.isFinite() && vacuumCoreSpeedFactor >= 1f) {
            "A latched pickup must outrun the Core"
        }
        require(minimumMagnetRadius.isFinite() && minimumMagnetRadius in collectionRadius..magnetRadius)
    }
}
