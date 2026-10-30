// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

/**
 * Cross-checks of the run-variety content against the rest of the catalog (each policy validates itself on
 * construction): personalities bias ordinary kinds only, world events end before the Architect, and the signal slots
 * sit between the site windows (one slot after each window's trial, gone before the next window opens) and fade
 * before the Architect. A switched-off system has no schedule to fit.
 */
internal fun validateRunVariety(data: ContentBootstrapData) {
    val variety = data.variety
    variety.director.personalities.forEach { personality ->
        personality.typeBias.forEach { bias ->
            val enemy = data.enemies.firstOrNull { it.type == bias.type }
            require(enemy != null && !enemy.isScheduled) {
                "Personality ${personality.id} may bias only ordinary kinds, not ${bias.type}"
            }
        }
    }

    val bossAt = data.tempo.bossAtSeconds
    val events = variety.worldEvents
    if (events.enabled) {
        require(events.lastEndSeconds < bossAt) {
            "World events must end before the Architect at $bossAt s, not at ${events.lastEndSeconds} s"
        }
    }

    val signals = variety.signals
    if (signals.enabled) {
        val windows = data.sites.windows
        require(signals.intervalSeconds == windows.offerInterval) {
            "Signal slots must repeat with the site windows every ${windows.offerInterval} s"
        }
        val firstTrialEnd = windows.firstOfferAt + windows.offerLifetime + windows.trialDuration
        require(signals.firstAtSeconds >= firstTrialEnd) {
            "The first signal must wait for the first site trial to end at $firstTrialEnd s"
        }
        val secondWindow = windows.firstOfferAt + windows.offerInterval
        require(signals.firstAtSeconds + signals.lifetimeSeconds <= secondWindow) {
            "A signal must fade before the next site window opens at $secondWindow s"
        }
        val lastSignalEnd = signals.firstAtSeconds + (signals.slots - 1) * signals.intervalSeconds + signals.lifetimeSeconds
        require(lastSignalEnd < bossAt) {
            "The last signal must fade before the Architect at $bossAt s, not at $lastSignalEnd s"
        }
    }
}
