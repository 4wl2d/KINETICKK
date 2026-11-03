// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.enemies

internal const val COMET_TELEGRAPH_SECONDS: Float = 0.6f
internal const val COMET_SPEED: Float = 720f
internal const val COMET_LANE_LENGTH: Float = 1_500f
/** The lane passes this far beside the Core's predicted position, on the Comet's own side. */
internal const val COMET_LANE_OFFSET: Float = 70f
internal const val COMET_RECOVERY_SECONDS: Float = 0.9f
/** Pause after a recovery before the next lane, at the base attack rate. */
internal const val COMET_COOLDOWN_SECONDS: Float = 1.8f
internal const val COMET_ENGAGE_RANGE: Float = 900f
/** Seconds of the Core's velocity the Comet's lane leads by. */
internal const val COMET_LEAD_SECONDS: Float = 0.45f
internal const val COMET_FIRST_LANE_SECONDS: Float = 1f
internal const val COMET_HOLD_FAR: Float = 620f
internal const val COMET_HOLD_NEAR: Float = 420f
internal const val COMET_RADIAL_SPEED: Float = 120f
internal const val COMET_ORBIT_SPEED: Float = 90f
internal const val COMET_AGILITY: Float = 2f

internal const val PYLON_DOCK_SEARCH: Float = 600f
/** Room a docked Pylon keeps between its body and its rock. */
internal const val PYLON_DOCK_GAP: Float = 6f
internal const val PYLON_SEEK_SECONDS: Float = 4f
internal const val PYLON_SEEK_SPEED: Float = 140f
internal const val PYLON_TELEGRAPH_SECONDS: Float = 0.8f
/** The sight line stops tracking the Core this long before the volley. */
internal const val PYLON_LOCK_SECONDS: Float = 0.2f
internal const val PYLON_RANGE: Float = 900f
internal const val PYLON_COOLDOWN_SECONDS: Float = 3f
internal const val PYLON_SHOT_SPEED: Float = 280f
internal const val PYLON_VOLLEY: Int = 3
internal const val PYLON_DENSE_VOLLEY: Int = 5
internal const val PYLON_VOLLEY_SPACING: Float = 0.16f
/** A seeking Pylon this close to its dock point mounts there. */
internal const val PYLON_DOCK_TOLERANCE: Float = 10f
/** A Pylon waits this long after mounting before its first sight line. */
internal const val PYLON_MOUNT_SECONDS: Float = 1f
internal const val PYLON_SIGHT_RADIUS: Float = 7f

internal const val SENTRY_MOUNT_DISTANCE: Float = 560f
internal const val SENTRY_TURN_RATE: Float = 2.4f
internal const val SENTRY_SIGHT_RANGE: Float = 760f
internal const val SENTRY_AIM_TOLERANCE: Float = 0.25f
internal const val SENTRY_TELEGRAPH_SECONDS: Float = 0.45f
internal const val SENTRY_COOLDOWN_SECONDS: Float = 1.6f
internal const val SENTRY_TRAIN: Int = 3
internal const val SENTRY_BOLT_SPEED: Float = 330f
internal const val SENTRY_BOLT_RADIUS: Float = 6f
internal const val SENTRY_BOLT_LIFE: Float = 2.4f
/** Gap between the bolts of one train along the Sentry's facing. */
internal const val SENTRY_BOLT_SPACING: Float = 28f
internal const val SENTRY_APPROACH_SPEED: Float = 90f
internal const val SENTRY_APPROACH_AGILITY: Float = 1.6f
internal const val SENTRY_SIGHT_RADIUS: Float = 6f

internal const val CRAWLER_HUG_RANGE: Float = 160f
internal const val CRAWLER_CRAWL_SPEED: Float = 150f
internal const val CRAWLER_LUNGE_DISTANCE: Float = 200f
internal const val CRAWLER_LUNGE_SPEED: Float = 230f
/** Gap from its centre to the surface a hugging Crawler steers toward. */
internal const val CRAWLER_HUG_GAP: Float = 6f
internal const val CRAWLER_LUNGE_AGILITY: Float = 3.5f
internal const val CRAWLER_AGILITY: Float = 3.2f
internal const val CRAWLER_HUG_GAIN: Float = 2.5f
internal const val CRAWLER_HUG_PUSH: Float = -40f
internal const val CRAWLER_HUG_PULL: Float = 60f
internal const val CRAWLER_SPACING: Float = 30f
internal const val CRAWLER_APPROACH: Float = 165f
internal const val CRAWLER_SWIRL: Float = 60f
internal const val CRAWLER_SEPARATION: Float = 110f

internal const val SERAPH_PULSE_SECONDS: Float = 3f
internal const val SERAPH_LINK_RANGE: Float = 260f
internal const val SERAPH_MAX_LINKS: Int = 2
/** Share of an ally's integrity one mend restores. */
internal const val SERAPH_HEAL_SHARE: Float = 0.06f
internal const val SERAPH_SHIELD_SECONDS: Float = 1.5f
internal const val SERAPH_HOLD_NEAR: Float = 380f
internal const val SERAPH_HOLD_FAR: Float = 520f
internal const val SERAPH_RETREAT_SPEED: Float = 90f
internal const val SERAPH_CLOSE_SPEED: Float = 70f
internal const val SERAPH_WEAVE: Float = 150f
internal const val SERAPH_WEAVE_RATE: Float = 2.4f
internal const val SERAPH_WEAVE_PHASE: Float = 0.9f
internal const val SERAPH_AGILITY: Float = 2.4f

internal const val CHOIR_PULSE_SECONDS: Float = 3.2f
internal const val CHOIR_TELEGRAPH_SECONDS: Float = 0.6f
/** A pulse shoves a Core within this reach of the Choir (plus the Core's radius). */
internal const val CHOIR_WAVE_RADIUS: Float = 300f
internal const val CHOIR_WAVE_PUSH: Float = 260f
internal const val CHOIR_VOLLEY: Int = 10
internal const val CHOIR_DENSE_VOLLEY: Int = 12
internal const val CHOIR_SHOT_SPEED: Float = 140f
/** Radians per second the pulse's bolt ring turns with the run clock. */
internal const val CHOIR_VOLLEY_SPIN: Float = 0.5f
internal const val CHOIR_HOLD_FAR: Float = 420f
internal const val CHOIR_HOLD_NEAR: Float = 300f
internal const val CHOIR_CLOSE_SPEED: Float = 60f
internal const val CHOIR_RETREAT_SPEED: Float = 56f
internal const val CHOIR_ORBIT_SPEED: Float = 30f
internal const val CHOIR_AGILITY: Float = 1.55f

internal const val IMP_PRIME_DISTANCE: Float = 130f
internal const val IMP_PRIME_SECONDS: Float = 0.5f
internal const val IMP_BLAST_RADIUS: Float = 95f
internal const val IMP_BLAST_DAMAGE: Float = 12f
internal const val IMP_WEAVE: Float = 110f
/** Embers a burst leaves: at its centre and this far to either side across its heading, after these delays. */
internal const val IMP_EMBER_SPREAD: Float = 60f
internal const val IMP_EMBER_SECONDS: Float = 0.5f
internal const val IMP_LEFT_EMBER_SECONDS: Float = 0.7f
internal const val IMP_RIGHT_EMBER_SECONDS: Float = 0.9f
/** A killed Imp leaves one ember after this delay. */
internal const val IMP_KILL_EMBER_SECONDS: Float = 0.6f
internal const val IMP_WEAVE_RATE: Float = 3.1f
internal const val IMP_AGILITY: Float = 1.8f

internal const val HOUND_WINDUP: Float = 0.35f
internal const val HOUND_DASH_SPEED: Float = 430f
internal const val HOUND_STRIKE_SECONDS: Float = 0.45f
internal const val HOUND_DASH_CADENCE: Float = 2.6f
internal const val HOUND_SPACING: Float = 46f
internal const val HOUND_APPROACH: Float = 125f
internal const val HOUND_SWIRL: Float = 50f
internal const val HOUND_SEPARATION: Float = 120f
internal const val HOUND_AGILITY: Float = 3f
/** Below this speed a Hound faces the Core instead of its motion. */
internal const val HOUND_FACING_SPEED: Float = 20f

internal const val CIPHER_GLITCH_SECONDS: Float = 3.4f
internal const val CIPHER_PHASE_SECONDS: Float = 0.35f
internal const val CIPHER_AIM_SECONDS: Float = 0.3f
internal const val CIPHER_VOLLEY: Int = 3
internal const val CIPHER_VOLLEY_SPACING: Float = 0.12f
internal const val CIPHER_SHOT_SPEED: Float = 260f
internal const val CIPHER_ENGAGE_RANGE: Float = 1_000f
/** A glitch puts the Cipher back between these distances from the Core. */
internal const val CIPHER_NEAREST: Float = 280f
internal const val CIPHER_FARTHEST: Float = 520f
/** A glitch turns the Cipher around the Core by 0.9-1.4 rad, either way. */
internal const val CIPHER_TURN: Float = 0.9f
internal const val CIPHER_TURN_SPREAD: Float = 0.5f
/** Salt of the stateless glitch hash (no run stream is drawn). */
internal const val CIPHER_SALT: Long = 0x0C1F_E55E_ED00_0013L
internal const val CIPHER_FIRST_GLITCH_SECONDS: Float = 2f
internal const val CIPHER_HASH_TICKS: Float = 120f
internal const val CIPHER_HOLD_FAR: Float = 420f
internal const val CIPHER_HOLD_NEAR: Float = 300f
internal const val CIPHER_CLOSE_SPEED: Float = 78f
internal const val CIPHER_RETREAT_SPEED: Float = 72f
internal const val CIPHER_ORBIT_SPEED: Float = 62f
internal const val CIPHER_AGILITY: Float = 2.4f

/** The Mirror steers this many times its offset from its mirror point, at most [MIRROR_MAX_SPEED]. */
internal const val MIRROR_GAIN: Float = 3f
internal const val MIRROR_MAX_SPEED: Float = 760f
internal const val MIRROR_AGILITY: Float = 3.5f
internal const val MIRROR_SHOT_SECONDS: Float = 3f
internal const val MIRROR_VOLLEY: Int = 2
internal const val MIRROR_VOLLEY_SPACING: Float = 0.2f
internal const val MIRROR_SHOT_SPEED: Float = 230f
