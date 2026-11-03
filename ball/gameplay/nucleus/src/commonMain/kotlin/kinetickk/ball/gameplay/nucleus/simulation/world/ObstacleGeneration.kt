// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.world

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.RebirthMutator
import kinetickk.ball.content.api.WorldLayer
import kinetickk.ball.gameplay.nucleus.model.Obstacle
import kinetickk.ball.gameplay.nucleus.model.ObstacleField
import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.model.lerp
import kinetickk.ball.gameplay.nucleus.render.ObstacleKind
import kotlin.math.floor
import kotlin.math.sqrt
import kinetickk.ball.gameplay.nucleus.simulation.*
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.variety.*

// ---------------------------------------------------------------------------------------------
// Generation.

/** A new run starts at the absolute origin with a fresh window and no destroyed asteroids. */
internal fun MutableGameState.resetObstacles() {
    worldOriginX = 0.0
    worldOriginY = 0.0
    obstacleField = ObstacleField.EMPTY
    destroyedObstacleIds = emptyList()
    obstacleHitReadyAt = 0f
    obstacleHitSurfaceId = NO_SURFACE_ID
    nearMissReadyAt = 0f
    reviveInvulnerableUntil = 0f
    coreObstacleGap = Float.POSITIVE_INFINITY
    grazeReadyAt = 0f
    slingWellId = NO_OBSTACLE_ID
    slingEntryHeading = 0f
    worldLayer = WorldLayer.BASE
    worldKeepOut = null
    refreshObstacleWindow()
}

/** Absolute chunk index of an absolute coordinate. */
internal fun MutableGameState.absoluteChunk(absolute: Double): Int =
    floor(absolute / content.obstacles.chunkSize).toInt()

/** Regenerates the obstacle window when the Core entered another chunk (or no window exists yet). */
internal fun MutableGameState.refreshObstacleWindow() {
    val tuning = content.obstacles
    val chunkX = absoluteChunk(worldOriginX + coreX)
    val chunkY = absoluteChunk(worldOriginY + coreY)
    val field = obstacleField
    if (field.span == tuning.windowChunks && field.centerChunkX == chunkX && field.centerChunkY == chunkY) return
    val span = tuning.windowChunks
    val half = span / 2
    val cells = ArrayList<List<Obstacle>>(span * span)
    val candidates = ChunkCandidateWindow(this, chunkX - half - 1, chunkY - half - 1, span)
    for (row in 0 until span) {
        for (column in 0 until span) {
            val cx = chunkX - half + column
            val cy = chunkY - half + row
            // Chunks still inside the window keep their obstacles (and any bumper cooldowns).
            // ObstacleField.of copies every slice into its new backing list.
            cells += field.chunkObstacles(cx, cy) ?: generateChunk(cx, cy, candidates)
        }
    }
    val next = ObstacleField.of(chunkX, chunkY, span, cells)
    check(next.size <= ContentBounds.MAX_OBSTACLES) { "The obstacle window exceeded ${ContentBounds.MAX_OBSTACLES}" }
    obstacleField = next
    refreshStructureField(chunkX, chunkY)
}

/**
 * The obstacles of one absolute chunk: a binomial count over the chunk's slots (mean scaled by Rebirth
 * density and Fractured Space), kinds and radii from the tuning, centres anywhere in the chunk (so no
 * obstacle-free corridor runs along chunk borders), a full gap from each other, none reaching into the
 * safe radius around the run origin. A candidate too close to a candidate of an earlier neighbour
 * chunk (lower row, or the same row to the left) yields to it, which keeps the gap across borders
 * while every chunk still depends only on its own hash and its neighbours'. Destroyed unstable
 * asteroids stay gone; they still count as candidates, so the rest of the field never moves.
 */
internal fun MutableGameState.generateChunk(chunkX: Int, chunkY: Int): List<Obstacle> =
    generateChunk(chunkX, chunkY, null)

private fun MutableGameState.generateChunk(
    chunkX: Int,
    chunkY: Int,
    candidates: ChunkCandidateWindow?,
): List<Obstacle> {
    val own = candidates?.get(chunkX, chunkY) ?: chunkCandidates(chunkX, chunkY)
    if (own.isEmpty()) return emptyList()
    val tuning = content.obstacles
    val chunk = tuning.chunkSize
    val neighbours = Array(EARLIER_NEIGHBOURS.size / 2) { neighbour ->
        val x = chunkX + EARLIER_NEIGHBOURS[neighbour * 2]
        val y = chunkY + EARLIER_NEIGHBOURS[neighbour * 2 + 1]
        candidates?.get(x, y) ?: chunkCandidates(x, y)
    }
    val result = ArrayList<Obstacle>(own.size)
    for (index in own.indices) {
        val candidate = own[index]
        if (yieldsToEarlierNeighbour(neighbours, candidate)) continue
        val id = obstacleId(chunkX, chunkY, candidate.slot, worldLayer)
        if (id in destroyedObstacleIds) continue
        if (keepOutCovers(chunkX, chunkY, candidate.offsetX, candidate.offsetY, candidate.kind, candidate.radius)) continue
        if (structureSuppresses(chunkX, chunkY, candidate.offsetX, candidate.offsetY, candidate.radius)) continue
        result += variantObstacle(
            chunkX, chunkY, candidate.slot, candidate.kind, candidate.radius, candidate.shapeSeed, id,
            x = (chunkX.toDouble() * chunk + candidate.offsetX - worldOriginX).toFloat(),
            y = (chunkY.toDouble() * chunk + candidate.offsetY - worldOriginY).toFloat(),
        )
    }
    return result
}

/** One placed obstacle of a chunk before cross-border resolution; offsets are within the chunk. */
private class ObstacleCandidate(
    val slot: Int,
    val kind: ObstacleKind,
    val radius: Float,
    val shapeSeed: Int,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * One refresh visits overlapping neighbour chunks repeatedly. Cache their pure candidates only for this refresh,
 * before destroyed ids, arrival keep-outs and structure suppression; nothing survives a layer or tuning change.
 * The earlier-neighbour stencil extends one column on both sides and one row below the requested window.
 */
private class ChunkCandidateWindow(
    private val state: MutableGameState,
    private val minimumX: Int,
    private val minimumY: Int,
    span: Int,
) {
    private val columns = span + 2
    private val cells = arrayOfNulls<List<ObstacleCandidate>>(columns * (span + 1))

    fun get(chunkX: Int, chunkY: Int): List<ObstacleCandidate> {
        val index = (chunkY - minimumY) * columns + chunkX - minimumX
        return cells[index] ?: state.chunkCandidates(chunkX, chunkY).also { cells[index] = it }
    }
}

/** [neighbours] holds the candidates of each [EARLIER_NEIGHBOURS] chunk, in that order. */
private fun MutableGameState.yieldsToEarlierNeighbour(
    neighbours: Array<List<ObstacleCandidate>>,
    candidate: ObstacleCandidate,
): Boolean {
    val tuning = content.obstacles
    val chunk = tuning.chunkSize
    for (neighbour in neighbours.indices) {
        val dx = EARLIER_NEIGHBOURS[neighbour * 2]
        val dy = EARLIER_NEIGHBOURS[neighbour * 2 + 1]
        val others = neighbours[neighbour]
        for (index in others.indices) {
            val other = others[index]
            val separationX = dx * chunk + other.offsetX - candidate.offsetX
            val separationY = dy * chunk + other.offsetY - candidate.offsetY
            val gap = other.radius + candidate.radius + tuning.minimumGap
            if (separationX * separationX + separationY * separationY < gap * gap) return true
        }
    }
    return false
}

/** Neighbour chunks generated "before" a chunk, as (dx, dy) pairs: the row below and the left one. */
private val EARLIER_NEIGHBOURS: IntArray = intArrayOf(-1, -1, 0, -1, 1, -1, -1, 0)

/** The chunk's own placements in the current world layer: deterministic from its hash, gap kept inside the chunk. */
private fun MutableGameState.chunkCandidates(chunkX: Int, chunkY: Int): List<ObstacleCandidate> {
    val tuning = content.obstacles
    val fractured = runMutatorActive(RebirthMutator.FRACTURED_SPACE)
    val mix = currentObstacleMix()
    val mean = tuning.meanPerChunk * rebirthProfile.obstacleDensityMultiplier *
        (if (fractured) tuning.fracturedDensityMultiplier else 1f) * mix.densityScale
    val slots = tuning.maximumPerChunk
    if (slots == 0) return emptyList()
    val chance = (mean / slots).coerceIn(0f, 1f)
    val random = HashRandom(chunkSeed(runSeed, chunkX, chunkY, worldLayer))
    var count = 0
    repeat(slots) { if (random.nextFloat() < chance) count++ }
    if (count == 0) return emptyList()
    val bumperShare = (if (fractured) tuning.fracturedBumperShare else tuning.bumperShare) * mix.bumperScale
    val chunk = tuning.chunkSize
    val baseX = chunkX.toDouble() * chunk
    val baseY = chunkY.toDouble() * chunk
    val placed = ArrayList<ObstacleCandidate>(count)
    for (slot in 0 until count) {
        val bumper = random.nextFloat() < bumperShare
        val unstable = random.nextFloat() < tuning.fracturedUnstableShare
        val size = random.nextFloat()
        val shapeSeed = random.nextInt()
        val kind = when {
            bumper -> ObstacleKind.BUMPER
            fractured && unstable -> ObstacleKind.UNSTABLE
            else -> ObstacleKind.ASTEROID
        }
        val radius = if (bumper) tuning.bumperRadius else lerp(tuning.asteroidMinimumRadius, tuning.asteroidMaximumRadius, size)
        for (attempt in 0 until PLACEMENT_ATTEMPTS) {
            val offsetX = random.nextFloat() * chunk
            val offsetY = random.nextFloat() * chunk
            val absoluteX = baseX + offsetX
            val absoluteY = baseY + offsetY
            if (sqrt(absoluteX * absoluteX + absoluteY * absoluteY) - radius < tuning.safeRadius) continue
            var clear = true
            for (index in placed.indices) {
                val other = placed[index]
                val gap = other.radius + radius + tuning.minimumGap
                if (distanceSquared(offsetX, offsetY, other.offsetX, other.offsetY) < gap * gap) {
                    clear = false
                    break
                }
            }
            if (!clear) continue
            placed += ObstacleCandidate(slot, kind, radius, shapeSeed, offsetX, offsetY)
            break
        }
    }
    return placed
}

private const val PLACEMENT_ATTEMPTS: Int = 8

/**
 * Stable obstacle id: absolute chunk (28 bits per axis), slot (4 bits) and the world layer in bits 60-62
 * (0 in realspace, so realspace ids are the pre-layer ones).
 */
internal fun obstacleId(chunkX: Int, chunkY: Int, slot: Int, layer: WorldLayer = WorldLayer.BASE): Long =
    ((layer.ordinal.toLong() and 0x7) shl 60) or ((chunkX.toLong() and CHUNK_ID_MASK) shl 32) or
        ((chunkY.toLong() and CHUNK_ID_MASK) shl 4) or (slot.toLong() and 0xF)

private const val CHUNK_ID_MASK: Long = 0x0FFF_FFFF

/** Stateless hash of the run seed, the layer and the absolute chunk, so generation order never matters. */
internal fun chunkSeed(runSeed: Int, chunkX: Int, chunkY: Int, layer: WorldLayer = WorldLayer.BASE): Long =
    worldHash(runSeed, layer, chunkX, chunkY, OBSTACLE_SEED_SALT)

private const val OBSTACLE_SEED_SALT: Long = 0x0B57_AC1E_5EED_0001L
