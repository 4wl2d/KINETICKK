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
}
