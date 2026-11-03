// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later
package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.distanceSquared
import kinetickk.ball.gameplay.nucleus.render.PickupType
import kinetickk.ball.gameplay.nucleus.simulation.structures.*
import kinetickk.ball.gameplay.nucleus.simulation.world.*

/**
 * Test-only opt-in pilot of the Totem chest probe: [DungeonPilot] attempts one dungeon after another over the whole
 * run, and between attempts the input-only NoPoi bot spends its Keys at the waiting Totem chests.
 * - The first attempt seeks from 90 s, as the dungeon gate's does; each later one from the frame the previous attempt
 *   ended with the Core outside every structure, until [DungeonPilot.SEEK_LEAD] seconds before the Architect. Then
 *   the bot flies as in the population runs (its own chest branch still opens a chest while it holds a Key).
 * - Outside a dungeon, in realspace, the bot steers instead of the seek while it holds a Key and a chest waits (it
 *   flies to the nearest chest and opens one charge per Key, taking the bot's Totem choice: Amplify, or a weapon change
 *   when its weapon is off its order) or while a Key lies within [KEY_REACH] (the seek flies past drops it would lose).
 *   An interrupted seek resumes with the same target.
 * - Inside a dungeon the pilot never yields to an errand: it fights the attempt as the dungeon gate's pilot does.
 * - The bot's cursor resyncs when it takes over from the pilot's steering.
 */
internal class DungeonCampaignPilot(private val bot: NoPoiBot) {
    private var pilot = DungeonPilot(bot)
    private val ended = ArrayList<DungeonAttempt>()
    private var heldCharges = 0
    private var piloted = false

    /** Every finished attempt, in order. */
    val attempts: List<DungeonAttempt> get() = ended

    /** Totem chest charges of the structures the attempts cleared, materialised or salvaged. */
    var chargesFound = 0
        private set

    /** A new attempt once the last one ended outside a structure; the bot's errand for a Key or a chest first. */
    fun control(s: MutableGameState) {
        if (pilot.attempt != null && s.dungeon == null && s.elapsed < s.content.tempo.bossAtSeconds - DungeonPilot.SEEK_LEAD) {
            pilot = DungeonPilot(bot, seekFrom = s.elapsed)
        }
        if (pilot.attempt == null && !pilot.inside && wantsErrand(s)) {
            if (piloted) bot.syncCursor(s)
            bot.control(s)
            piloted = false
            return
        }
        pilot.control(s)
        piloted = true
    }

    /** The bot's choices. */
    fun choose(s: MutableGameState) = bot.choose(s)

    /** Reads the current attempt after a simulated input frame (and after the run ended). */
    fun observe(s: MutableGameState) {
        s.dungeon?.let { heldCharges = it.chestCharges }
        val before = pilot.attempt
        pilot.observe(s)
        val attempt = pilot.attempt ?: return
        if (before != null) return
        ended += attempt
        if (attempt.outcome == DungeonOutcome.CLEARED) chargesFound += heldCharges
    }

    /** True outside a dungeon in realspace with a Key and a waiting chest, or with a Key pickup within [KEY_REACH]. */
    private fun wantsErrand(s: MutableGameState): Boolean {
        if (s.dungeon != null || !s.inBaseLayer()) return false
        if (s.keys > 0 && s.structures.totemChests.isNotEmpty()) return true
        for (pickup in s.pickups) {
            if (pickup.type == PickupType.KEY && distanceSquared(s.coreX, s.coreY, pickup.x, pickup.y) < KEY_REACH * KEY_REACH) return true
        }
        return false
    }

    companion object {
        /** A Key pickup this close sends the bot to collect it before the seek goes on. */
        const val KEY_REACH = 1_200f
    }
}
