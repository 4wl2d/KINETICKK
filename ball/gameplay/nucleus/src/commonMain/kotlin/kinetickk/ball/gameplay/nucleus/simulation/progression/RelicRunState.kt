// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation.progression

import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.gameplay.nucleus.model.EchoHit
import kinetickk.ball.gameplay.nucleus.model.RunFloatArray
import kinetickk.ball.gameplay.nucleus.model.RunIntArray
import kinetickk.ball.gameplay.nucleus.model.SeveredRelic
import kinetickk.ball.profile.api.GameplayProfileSnapshot

/**
 * Relic ranks, cooldowns and counters, echo hits, severed Relics, Ouroboros, the Heirloom offer, and the run's Relic budget
 * and Melds. Ranks, severing, the Heirloom, the budget and Melds are written by relic progression; cooldowns, counters,
 * echo hits and Ouroboros by the relic rules.
 */
internal class RelicRunState(
    content: GameplayContentSnapshot,
    source: RelicRunState?,
    bootstrapProgress: GameplayProfileSnapshot?,
) {
    internal val relicRanks: RunIntArray = source?.relicRanks?.copy()
        ?: RunIntArray(IntArray(content.relics.size))
    internal val relicCooldowns: RunFloatArray = source?.relicCooldowns?.copy()
        ?: RunFloatArray(FloatArray(content.relics.size))
    internal val relicCounters: RunIntArray = source?.relicCounters?.copy()
        ?: RunIntArray(IntArray(content.relics.size))
    internal val echoHits: MutableList<EchoHit> = source?.echoHits
        ?.let { hits ->
            hits.mapTo(ArrayList(hits.size), EchoHit::copy)
        }
        ?: mutableListOf()
    internal var severedRelics: List<SeveredRelic> = source?.severedRelics ?: emptyList()
    internal var ouroborosSpent: Boolean = source?.ouroborosSpent ?: false
    internal var ouroborosLockedUntil: Float = source?.ouroborosLockedUntil ?: 0f
    internal var heirloomCandidates: List<RelicId> = source?.heirloomCandidates
        ?: bootstrapProgress?.collection?.heirloomRelicIds?.toList().orEmpty()
    internal var heirloomPending: Boolean = source?.heirloomPending ?: false
    internal var heirloomOfferActive: Boolean = source?.heirloomOfferActive ?: false
    internal var pendingHeirloomResolved: Boolean = source?.pendingHeirloomResolved ?: false
    internal var pendingVictoryRelics: List<RelicId>? = source?.pendingVictoryRelics
    /** Relic opportunities claimed this run: every Relic offer opened and every Heirloom taken (the budget's count). */
    internal var relicClaims: Int = source?.relicClaims ?: 0
    /** Relic slots sealed by a Meld this run; they stay empty and no Relic binds there. */
    internal var meldedSlots: Int = source?.meldedSlots ?: 0
    /** The Melds' multiplier on all damage dealt (1 without a Meld). */
    internal var meldDamageMultiplier: Float = source?.meldDamageMultiplier ?: 1f

    /**
     * A new run owns no Relic and no echo hit, has severed none, spent no Ouroboros, claimed no Relic opportunity and
     * melded no slot, and shows no Heirloom offer. The Heirloom candidates, the pending Heirloom flag, its resolution
     * and the victory Relics are not touched.
     */
    fun reset() {
        relicRanks.fill(0)
        relicCooldowns.fill(0f)
        relicCounters.fill(0)
        echoHits.clear()
        severedRelics = emptyList()
        ouroborosSpent = false
        ouroborosLockedUntil = 0f
        heirloomOfferActive = false
        relicClaims = 0
        meldedSlots = 0
        meldDamageMultiplier = 1f
    }
}
