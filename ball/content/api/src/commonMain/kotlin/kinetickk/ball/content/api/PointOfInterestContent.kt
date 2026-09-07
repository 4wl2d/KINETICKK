// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf

enum class PointOfInterestKind { RESONANT_CIRCUIT, SEALED_ANOMALY, COLLAPSING_ORBIT }
enum class DirectedReward { WEAPON, RELIC, ITEM_AND_REPAIR }
enum class RewardFocus(val label: String) {
    MOTION("Motion"), CONTROL("Control"), STRIKE("Strike"),
    VECTOR("Vector"), GRAVITIC("Gravitic"), ION("Ion"),
    RIFT("Rift"), PRISM("Prism"), ENTROPY("Entropy"),
    OFFENSE("Offense"), DEFENSE("Defense"), ECONOMY("Economy"),
}

data class PointOfInterestDefinition(
    val kind: PointOfInterestKind,
    val name: String,
    val instruction: String,
    val reward: DirectedReward,
)

/** Captured Content policy: all clocks use active simulation seconds. */
data class PointOfInterestPolicy(
    val firstOfferAt: Float = 45f,
    val offerInterval: Float = 120f,
    val lastOfferAt: Float = 645f,
    val offerLifetime: Float = 40f,
    val trialDuration: Float = 25f,
    val orbitRequiredSeconds: Float = 8f,
) {
    init {
        require(firstOfferAt.isFinite() && firstOfferAt >= 0f)
        require(offerInterval.isFinite() && offerInterval > 0f)
        require(lastOfferAt.isFinite() && lastOfferAt >= firstOfferAt)
        require(offerLifetime.isFinite() && offerLifetime in 1f..40f)
        require(trialDuration.isFinite() && trialDuration in 1f..25f)
        require(orbitRequiredSeconds.isFinite() && orbitRequiredSeconds in 1f..trialDuration)
        require((lastOfferAt - firstOfferAt) / offerInterval < 6f)
    }

    val definitions: ImmutableList<PointOfInterestDefinition> get() = DefaultPointsOfInterest
    fun definition(kind: PointOfInterestKind): PointOfInterestDefinition = definitions.first { it.kind == kind }
}

private val DefaultPointsOfInterest = immutableListOf(
    PointOfInterestDefinition(PointOfInterestKind.RESONANT_CIRCUIT, "Resonant circuit", "Visit beacons 2, 3, then return to 1.", DirectedReward.WEAPON),
    PointOfInterestDefinition(PointOfInterestKind.SEALED_ANOMALY, "Sealed anomaly", "Defeat the three marked defenders near the vault.", DirectedReward.RELIC),
    PointOfInterestDefinition(PointOfInterestKind.COLLAPSING_ORBIT, "Collapsing orbit", "Move within the ring for eight seconds; dodge marked volleys.", DirectedReward.ITEM_AND_REPAIR),
)
