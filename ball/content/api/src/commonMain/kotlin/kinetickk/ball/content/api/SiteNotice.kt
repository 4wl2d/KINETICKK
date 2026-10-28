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
}
