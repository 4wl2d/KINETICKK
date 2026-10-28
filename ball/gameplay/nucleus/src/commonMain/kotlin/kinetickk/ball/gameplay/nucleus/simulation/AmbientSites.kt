// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.AmbientSiteRule
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.SiteNotice
import kinetickk.ball.content.api.WorldDiscovery
import kinetickk.ball.gameplay.nucleus.model.AmbientSiteWindow
import kinetickk.ball.gameplay.nucleus.model.RunSecretFlag
import kinetickk.ball.gameplay.nucleus.model.SiteState
import kinetickk.ball.gameplay.nucleus.model.TAU
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.segmentCircleIntersects
import kinetickk.ball.gameplay.nucleus.protocol.GameplayAudioCue
import kinetickk.foundation.collections.toImmutableList
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Ambient sites: drift caches and easter eggs placed by a stateless hash of the run seed and an absolute cell, only far
 * from the run's spawn. The window around the Core is rebuilt when the Core changes obstacle chunk, a site is moved
 * clear of obstacles once the Core comes near it, and a cache opens when the Core flies through it. None of it draws
 * from a run stream.
 */

/** Placement seeds per ambient kind: `worldHash(runSeed, cellX, cellY, salt)` (registered SITES salts). */
internal const val AMBIENT_CACHE_SALT: Long = 0x517E_C0A5_0000_0011L
internal const val AMBIENT_LOST_CURSOR_SALT: Long = 0x517E_C0A5_0000_0012L
internal const val AMBIENT_GOLDEN_SALT: Long = 0x517E_C0A5_0000_0013L
internal const val AMBIENT_QUIET_SALT: Long = 0x517E_C0A5_0000_0014L

/** Contents of a drift cache: `worldHash(runSeed, cellX, cellY, CACHE_CONTENTS_SALT)` (registered SITES salt). */
internal const val CACHE_CONTENTS_SALT: Long = 0x517E_C0A5_0000_0021L

/** A structure footprint grown by this margin keeps ambient sites out. */
internal const val AMBIENT_STRUCTURE_MARGIN: Float = 300f

/** Settling clears a circle this much wider than the site's activation radius, in at most six 60 u moves. */
internal const val AMBIENT_SETTLE_CLEARANCE: Float = 40f
internal const val AMBIENT_SETTLE_STEP: Float = 60f
internal const val AMBIENT_SETTLE_ATTEMPTS: Int = 6

/** Pilots that must not opt in avoid the ambient sites within this distance of the Core. */
internal const val AMBIENT_MARK_REACH: Float = 4_000f

/** Fx colour of an opened cache's burst (bone). */
private const val CACHE_BURST_COLOR: Int = 1

/** A cell coordinate keeps 28 bits in an ambient site id. */
private const val AMBIENT_CELL_MASK: Long = 0x0FFF_FFFFL

/** Ambient site id: the kind (ordinal + 1) from bit 56, then the cell's x and y in 28 bits each; never a window id. */
internal fun ambientSiteId(kind: SiteKind, cellX: Int, cellY: Int): Long =
    ((kind.ordinal + 1).toLong() shl 56) or ((cellX.toLong() and AMBIENT_CELL_MASK) shl 28) or
        (cellY.toLong() and AMBIENT_CELL_MASK)

/** The absolute cell x of an ambient site id (sign-extended from its 28 bits). */
internal fun ambientCellX(id: Long): Int = (((id ushr 28) and AMBIENT_CELL_MASK).toInt() shl 4) shr 4

/** The absolute cell y of an ambient site id (sign-extended from its 28 bits). */
internal fun ambientCellY(id: Long): Int = ((id and AMBIENT_CELL_MASK).toInt() shl 4) shr 4

/** Where a rule places a site in one absolute cell (absolute coordinates). */
internal data class AmbientCandidate(val absoluteX: Double, val absoluteY: Double)

/**
 * The ambient step after the window sites: realspace only, the window rebuilt on an obstacle-chunk change, nearby sites
 * settled, then the caches the Core flew through opened (during any engagement too). An [AmbientSiteWindow.EMPTY]
 * window stays empty: nothing ambient is placed while the policy is off, after the Architect arrives or in a quiet
 * arena.
 */
internal fun MutableGameState.updateAmbientSites() {
    if (!content.sites.ambient.enabled) {
        ambientSites = AmbientSiteWindow.EMPTY
        return
    }
    if (!inBaseLayer()) {
        // Outside realspace nothing ambient exists; the window is built again on the way back.
        ambientSites = AmbientSiteWindow.UNBUILT
        return
    }
    if (ambientSites == AmbientSiteWindow.EMPTY) return
    val absoluteX = worldOriginX + coreX
    val absoluteY = worldOriginY + coreY
    val chunkX = absoluteChunk(absoluteX)
    val chunkY = absoluteChunk(absoluteY)
    if (chunkX != ambientSites.chunkX || chunkY != ambientSites.chunkY) {
        ambientSites = generateAmbientWindow(absoluteX, absoluteY, chunkX, chunkY)
    }
    settleNearbyAmbientSites()
    touchAmbientSites()
}

/**
 * Every enabled rule's sites in the cells within the policy's query half size of the Core, rule by rule in row-major
 * cell order: never an opened site, a found egg or a site inside a structure footprint. A site the old window already
 * held keeps its state (and so its settled position).
 */
private fun MutableGameState.generateAmbientWindow(
    absoluteX: Double,
    absoluteY: Double,
    chunkX: Int,
    chunkY: Int,
): AmbientSiteWindow {
    val policy = content.sites.ambient
    val previous = ambientSites.sites
    val half = policy.queryHalfSize.toDouble()
    val placed = ArrayList<SiteState>()
    for (rule in policy.rules) {
        if (!rule.enabled || rule.maxChance <= 0f || eggFound(rule.kind)) continue
        val cell = rule.cellSize.toDouble()
        for (cellY in floor((absoluteY - half) / cell).toInt()..floor((absoluteY + half) / cell).toInt()) {
            for (cellX in floor((absoluteX - half) / cell).toInt()..floor((absoluteX + half) / cell).toInt()) {
                val id = ambientSiteId(rule.kind, cellX, cellY)
                if (id in openedSiteIds) continue
                val candidate = ambientCandidate(rule, cellX, cellY) ?: continue
                if (structureFootprintContains(candidate.absoluteX, candidate.absoluteY, AMBIENT_STRUCTURE_MARGIN)) continue
                placed += previous.firstOrNull { it.id == id } ?: SiteState(
                    id = id,
                    kind = rule.kind,
                    x = (candidate.absoluteX - worldOriginX).toFloat(),
                    y = (candidate.absoluteY - worldOriginY).toFloat(),
                    expiresAt = Float.POSITIVE_INFINITY,
                    settled = false,
                )
            }
        }
    }
    check(placed.size <= ContentBounds.MAX_AMBIENT_SITES) { "The ambient window exceeded ${ContentBounds.MAX_AMBIENT_SITES}" }
    return AmbientSiteWindow(chunkX, chunkY, placed.toImmutableList())
}

/** A found Lost Cursor or Quiet Signal is never placed again this run. */
private fun MutableGameState.eggFound(kind: SiteKind): Boolean =
    kind == SiteKind.LOST_CURSOR && hasSecret(RunSecretFlag.LOST_CURSOR_FOUND) ||
        kind == SiteKind.QUIET_SIGNAL && hasSecret(RunSecretFlag.QUIET_SIGNAL_FOUND)

/**
 * The site [rule] places in absolute cell ([cellX], [cellY]), or null: the chance is 0 while the cell's centre lies
 * within the rule's minimum distance of the absolute origin and rises linearly to its maximum at the full distance; a
 * placed site lies in the inner 60 % of its cell and itself at least the minimum distance from the origin. A pure
 * function of the run seed, the cell and the kind's salt.
 */
internal fun MutableGameState.ambientCandidate(rule: AmbientSiteRule, cellX: Int, cellY: Int): AmbientCandidate? {
    val random = HashRandom(worldHash(runSeed, cellX, cellY, ambientSalt(rule.kind)))
    val cell = rule.cellSize.toDouble()
    val centreX = (cellX + 0.5) * cell
    val centreY = (cellY + 0.5) * cell
    val distance = sqrt(centreX * centreX + centreY * centreY)
    val minimum = rule.minDistance.toDouble()
    val chance = if (distance < minimum) {
        0f
    } else {
        rule.maxChance * ((distance - minimum) / (rule.fullDistance - minimum)).coerceIn(0.0, 1.0).toFloat()
    }
    if (random.nextFloat() >= chance) return null
    val absoluteX = cellX * cell + (0.2 + 0.6 * random.nextFloat()) * cell
    val absoluteY = cellY * cell + (0.2 + 0.6 * random.nextFloat()) * cell
    if (absoluteX * absoluteX + absoluteY * absoluteY < minimum * minimum) return null
    return AmbientCandidate(absoluteX, absoluteY)
}

/** The placement salt of an ambient kind (content admits only these four kinds as ambient rules). */
private fun ambientSalt(kind: SiteKind): Long = when (kind) {
    SiteKind.DRIFT_CACHE -> AMBIENT_CACHE_SALT
    SiteKind.LOST_CURSOR -> AMBIENT_LOST_CURSOR_SALT
    SiteKind.GOLDEN_DRIFTER -> AMBIENT_GOLDEN_SALT
    SiteKind.QUIET_SIGNAL -> AMBIENT_QUIET_SALT
    else -> error("$kind is never placed by an ambient rule")
}

/**
 * Moves every unsettled site within the settle distance of the Core once: outward along a direction hashed from its id,
 * in 60 u steps until its activation circle (plus 40 u) is clear of obstacles and walls, at most six steps. That
 * neighbourhood lies inside the current obstacle window, so a settled position depends only on the run seed.
 */
private fun MutableGameState.settleNearbyAmbientSites() {
    val window = ambientSites
    val sites = window.sites
    val reach = content.sites.ambient.settleDistance
    var settled: MutableList<SiteState>? = null
    for (index in sites.indices) {
        val site = sites[index]
        if (site.settled || distanceSquared(coreX, coreY, site.x, site.y) > reach * reach) continue
        val angle = TAU * HashRandom(site.id).nextFloat()
        val point = nudgedFrom(
            site.x - cos(angle), site.y - sin(angle), site.x, site.y,
            content.sites.definition(site.kind).activationRadius + AMBIENT_SETTLE_CLEARANCE,
            AMBIENT_SETTLE_STEP, AMBIENT_SETTLE_ATTEMPTS,
        )
        val next = settled ?: sites.toMutableList().also { settled = it }
        next[index] = site.copy(x = point.x, y = point.y, settled = true)
    }
    settled?.let { ambientSites = window.copy(sites = it.toImmutableList()) }
}

/** Opens every settled cache the Core's swept step crossed this step. */
private fun MutableGameState.touchAmbientSites() {
    val sites = ambientSites.sites
    for (index in sites.indices) {
        val site = sites[index]
        if (!site.settled || !opensOnFlyThrough(site.kind)) continue
        val radius = content.sites.definition(site.kind).activationRadius
        if (segmentCircleIntersects(previousCoreX, previousCoreY, coreX, coreY, site.x, site.y, radius)) openAmbientSite(site)
    }
}

/**
 * Whether flying through a site of [kind] opens it here: drift caches. The easter eggs are found by their own rules
 * (the Golden Drifter's trigger is its proximity, never a fly-through) and stay in place until then.
 */
private fun opensOnFlyThrough(kind: SiteKind): Boolean = kind == SiteKind.DRIFT_CACHE

/**
 * Opens [site]: its id joins the opened-site FIFO (the oldest of more than [ContentBounds.MAX_OPENED_SITES] drops
 * out), it leaves the window, and a cache grants its contents.
 */
private fun MutableGameState.openAmbientSite(site: SiteState) {
    openedSiteIds = (openedSiteIds + site.id).takeLast(ContentBounds.MAX_OPENED_SITES)
    ambientSites = ambientSites.copy(sites = ambientSites.sites.filter { it.id != site.id }.toImmutableList())
    openDriftCache(site)
}

/**
 * A drift cache's contents, hashed from its cell: Data (with Matter), repairs (with Matter) or a Relic by the policy's
 * shares; the notice first, then the discovery and the grant.
 */
private fun MutableGameState.openDriftCache(site: SiteState) {
    val policy = content.sites.ambient
    val cellX = ambientCellX(site.id)
    val cellY = ambientCellY(site.id)
    val roll = HashRandom(worldHash(runSeed, cellX, cellY, CACHE_CONTENTS_SALT)).nextFloat()
    val reward = when {
        roll < policy.cacheDataShare -> policy.cacheData
        roll < policy.cacheDataShare + policy.cacheRepairShare -> policy.cacheRepairs
        else -> policy.cacheRelic
    }
    message = SiteNotice.CACHE_OPENED.english
    messageTime = 2f
    discoverWorld(WorldDiscovery.DRIFT_CACHE)
    emitSound(GameplayAudioCue.PICKUP)
    burst(site.x, site.y, 12, CACHE_BURST_COLOR)
    grantSiteReward(reward, site.x, site.y, worldHash(runSeed, cellX, cellY, REWARD_SCATTER_SALT))
}

/**
 * Writes the marks of the unopened ambient sites within [AMBIENT_MARK_REACH] of the Core after [count] marks: a
 * fly-through site's activation radius, a proximity trigger's trigger radius. Never past [MAX_OPTIONAL_SITE_MARKS].
 */
internal fun MutableGameState.appendAmbientSiteMarks(out: FloatArray, count: Int): Int {
    var written = count
    val sites = ambientSites.sites
    for (index in sites.indices) {
        if (written >= MAX_OPTIONAL_SITE_MARKS) break
        val site = sites[index]
        if (distanceSquared(coreX, coreY, site.x, site.y) > AMBIENT_MARK_REACH * AMBIENT_MARK_REACH) continue
        val base = written * 3
        out[base] = site.x
        out[base + 1] = site.y
        out[base + 2] = ambientMarkRadius(site.kind)
        written++
    }
    return written
}

/** The circle a pilot must keep out of: a proximity trigger's radius, else the fly-through activation radius. */
private fun MutableGameState.ambientMarkRadius(kind: SiteKind): Float {
    val rules = content.sites.ambient.rules
    for (index in rules.indices) {
        val rule = rules[index]
        if (rule.kind == kind && rule.triggerRadius > 0f) return rule.triggerRadius
    }
    return content.sites.definition(kind).activationRadius
}
