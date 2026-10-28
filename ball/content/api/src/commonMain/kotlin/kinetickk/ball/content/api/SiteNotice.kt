// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Run notices the site systems emit. Each [english] text is a content key (Russian in the site phrase list, or in the
 * shared list for the 0.5.0 anomaly notices); ContentLocalizationTest iterates every entry.
 */
enum class SiteNotice(val english: String) {
    TWO_ANOMALIES("TWO ANOMALIES DETECTED // CHOOSE A COURSE"),
    /** A window pair with a kind outside the anomalies (a pair with a rift says DimensionNotices.RIFT_DETECTED). */
    TWO_SITES("TWO SITES DETECTED // CHOOSE A COURSE"),
    ANOMALY_LOST("ANOMALY LOST"),
    ANOMALY_RESOLVED("ANOMALY RESOLVED"),
    REWARD_SALVAGED("REWARD SALVAGED"),
    /** A finished gate race, by the best medal its clock earned (the tail is shown in the feed). */
    RACE_GOLD("RACE FINISHED // GOLD"),
    RACE_SILVER("RACE FINISHED // SILVER"),
    RACE_BRONZE("RACE FINISHED // BRONZE"),
    /** Finished past the bronze time: the finish reward only. */
    RACE_FINISHED("RACE FINISHED"),
    /** Timed out, or left the course: nothing is granted. */
    RACE_LOST("RACE LOST"),
    /** A challenge's rule was met in time: its reward is granted. */
    CHALLENGE_COMPLETE("CHALLENGE COMPLETE"),
    /** A challenge's rule was broken, its time ran out or the Core left it: nothing is granted. */
    CHALLENGE_FAILED("CHALLENGE FAILED"),
    /** The bounty's mini-boss fell: the site adds its Matter to the boss's own drops. */
    BOUNTY_CLAIMED("BOUNTY CLAIMED"),
    /** The Core left the arena (or the boss left without a kill): the boss is gone and nothing is granted. */
    BOUNTY_ABANDONED("BOUNTY ABANDONED"),
    /** The Core flew through a drift cache: its contents are granted. */
    CACHE_OPENED("CACHE OPENED"),
    /** The Core flew through the Lost Cursor: it follows the Core for the rest of the run. */
    LOST_CURSOR_FOUND("LOST CURSOR FOUND"),
    /** The Golden Drifter was released and flees. */
    GOLDEN_SIGHTED("GOLDEN DRIFTER"),
    GOLDEN_CAUGHT("GOLDEN DRIFTER CAUGHT"),
    /** The Golden Drifter outran its time or the Core: it leaves without a kill. */
    GOLDEN_ESCAPED("IT GOT AWAY"),
    SPIN_CYCLE("SPIN CYCLE"),
    /** STRUCTURES' Doodle reached (`discoverDoodle`). */
    DOODLE("THE ARCHITECT'S DOODLE"),
    /** The Quiet Signal heard (the tail is shown in the feed). */
    QUIET_SIGNAL("QUIET SIGNAL // THE ARCHITECT WAS NOT THE FIRST"),
    /** Far travel placed the Cartographer's sigil ahead of the Core. */
    SIGIL_APPEARS("A SIGIL AWAKENS"),
    /** The sigil was never touched before its cutoff, or its duel ended without the boss's fall. */
    SIGIL_FADES("THE SIGIL FADES"),
    /** The sigil's boss fell: the sigil pays its reward. */
    SIGIL_BROKEN("SIGIL BROKEN"),
}
