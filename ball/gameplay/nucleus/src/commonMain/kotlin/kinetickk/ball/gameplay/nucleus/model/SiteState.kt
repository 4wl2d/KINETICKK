// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.SiteKind
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.collections.toImmutableList

internal data class SitePoint(val dx: Float, val dy: Float)

/** Circuit beacon offsets in visit order (step 0, 1, 2; the third beacon is the centre). */
internal val CircuitBeacons: ImmutableList<SitePoint> =
    immutableListOf(SitePoint(230f, 200f), SitePoint(-230f, 200f), SitePoint(0f, 0f))

/** Value-only runtime of one site; reducers may safely retain prior snapshots. */
internal data class SiteState(
    /** Window: offerId × 2 + side; ambient sites and sigils carry their own id ranges. */
    val id: Long,
    val kind: SiteKind,
    val x: Float,
    val y: Float,
    /** Window offers close here; ambient sites and sigils use Float.POSITIVE_INFINITY. */
    val expiresAt: Float,
    val active: Boolean = false,
    /** Seconds left of a timed trial (25 = the window cap until activation sets it). */
    val remaining: Float = 25f,
    /** Seconds since activation (race clock, ring shrink, grace). */
    val clock: Float = 0f,
    /** Next course mark: circuit beacon 0..2 (3 = done), race gate, slalom pole. */
    val step: Int = 0,
    /** Defeated defenders / Dash kills. */
    val count: Int = 0,
    /** Orbit, ring, untouched or velocity seconds earned. */
    val meter: Float = 0f,
    val volleyClock: Float = 2f,
    val warningRemaining: Float = 0f,
    val volleyAngle: Float = 0f,
    /** Untouched volley aim point (run-local coordinates at the warning's start). */
    val aimX: Float = 0f,
    val aimY: Float = 0f,
    /** Enemies the site owns: vault defenders, marked Ram foes, the bounty or secret boss. */
    val foeIds: ImmutableList<Int> = immutableListOf(),
    /** Course offsets from (x, y): circuit beacons, race gates or slalom poles, in order. */
    val course: ImmutableList<SitePoint> = immutableListOf(),
    /** Slalom: bit k set = pole k is passed on its left. */
    val sides: Int = 0,
    /** Race: par seconds; slalom: course axis angle (radians). */
    val par: Float = 0f,
    val axis: Float = 0f,
    /** Race: seconds allowed (medal limit). */
    val limit: Float = 0f,
    /** Untouched: damage taken + absorbed when it started. */
    val hurtBaseline: Double = 0.0,
    /** Ambient: false until nudged clear of obstacles (settled once, inside the obstacle window). */
    val settled: Boolean = true,
) {
    val offerId: Int get() = (id ushr 1).toInt()
    val side: Int get() = (id and 1L).toInt()

    fun shifted(dx: Float, dy: Float): SiteState = copy(x = x - dx, y = y - dy, aimX = aimX - dx, aimY = aimY - dy)
}

/** Window site id of an offer's side. */
internal fun windowSiteId(offerId: Int, side: Int): Long = offerId.toLong() * 2L + side

/** Bit = ordinal in MutableGameState.runSecretFlags (at most 31 entries). */
internal enum class RunSecretFlag {
    LOST_CURSOR_FOUND, GOLDEN_DRIFTER_SPAWNED, GOLDEN_DRIFTER_CAUGHT, SPIN_CYCLE, DOODLE_FOUND, QUIET_SIGNAL_FOUND,
    FAR_TRAVEL, FAR_SIGIL_SPAWNED, CARTOGRAPHER_SUMMONED, CARTOGRAPHER_DEFEATED,
}

/** Flags that only exist because the player opted into something (FAR_TRAVEL is not one). */
internal val EngagementSecretMask: Int = RunSecretFlag.entries
    .filter { it != RunSecretFlag.FAR_TRAVEL }
    .fold(0) { mask, flag -> mask or (1 shl flag.ordinal) }

/**
 * Immutable window of hash-placed sites around the Core (at most MAX_AMBIENT_SITES by construction), built for the
 * absolute obstacle chunk ([chunkX], [chunkY]) the Core was in.
 */
internal data class AmbientSiteWindow(val chunkX: Int, val chunkY: Int, val sites: ImmutableList<SiteState>) {
    fun shifted(dx: Float, dy: Float): AmbientSiteWindow =
        if (sites.isEmpty()) this else copy(sites = sites.map { it.shifted(dx, dy) }.toImmutableList())

    companion object {
        /** No ambient sites, and the ambient step builds none (a disabled policy, the Architect's fight, a quiet arena). */
        val EMPTY: AmbientSiteWindow = AmbientSiteWindow(Int.MIN_VALUE, Int.MIN_VALUE, immutableListOf())

        /** No window yet: the next ambient step builds one around the Core (a new run, a return to realspace). */
        val UNBUILT: AmbientSiteWindow = AmbientSiteWindow(Int.MAX_VALUE, Int.MAX_VALUE, immutableListOf())
    }
}
